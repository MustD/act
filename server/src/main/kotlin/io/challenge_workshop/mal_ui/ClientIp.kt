package io.challenge_workshop.mal_ui

import java.net.InetAddress

/** A CIDR block (`10.0.0.0/8`, `fd00::/8`) or a single address, for the trusted-proxy list. */
class Cidr private constructor(private val network: ByteArray, private val prefix: Int) {
    fun contains(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != network.size) return false
        val whole = prefix / 8
        for (i in 0 until whole) if (bytes[i] != network[i]) return false
        val rest = prefix % 8
        if (rest == 0) return true
        val mask = (0xFF shl (8 - rest)) and 0xFF
        return (bytes[whole].toInt() and mask) == (network[whole].toInt() and mask)
    }

    companion object {
        fun parse(text: String): Cidr {
            val network = InetAddress.getByName(text.substringBefore('/').trim()).address
            val prefix = text.substringAfter('/', "").trim().takeIf { it.isNotEmpty() }
                ?.let { it.toIntOrNull()?.takeIf { p -> p in 0..network.size * 8 } ?: error("Bad CIDR prefix in '$text'") }
                ?: (network.size * 8)
            return Cidr(network, prefix)
        }
    }
}

private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

/** Parses only literal addresses: forwarded hops are client-supplied, and a hostname would trigger a DNS lookup. */
private fun literalIp(text: String): InetAddress? =
    if (IPV4.matches(text) || (':' in text && text.all { it.isLetterOrDigit() || it == ':' || it == '.' })) {
        runCatching { InetAddress.getByName(text) }.getOrNull()
    } else null

/** One bucket per IPv6 /64, since a single subscriber can rotate through the whole prefix. */
fun rateLimitKey(ip: String): String =
    literalIp(ip)?.takeIf { it.address.size == 16 }
        ?.let { it.address.copyOf(8).joinToString("") { b -> "%02x".format(b) } + "::/64" } ?: ip

/**
 * The address a request counts against: the socket peer, unless the peer is a trusted proxy,
 * in which case the right-most `X-Forwarded-For` entry that is not itself a trusted proxy.
 * Walking from the right is what makes a client-supplied prefix harmless — every hop appends,
 * so only entries added by trusted hops are believed.
 */
fun clientIp(peer: String, forwardedFor: String?, trusted: List<Cidr>): String {
    fun trusts(ip: InetAddress?) = ip != null && trusted.any { it.contains(ip) }

    // The peer comes from the socket, so it is trusted to be well-formed (the test host reports "localhost").
    val peerIp = runCatching { InetAddress.getByName(peer) }.getOrNull()
    if (!trusts(peerIp) || forwardedFor == null) return peer
    val hops = forwardedFor.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val client = hops.lastOrNull { !trusts(literalIp(it)) } ?: hops.firstOrNull()
    return client?.takeIf { literalIp(it) != null } ?: peer
}

package io.challenge_workshop.mal_ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientIpTest {
    private val trusted = listOf(Cidr.parse("172.18.0.0/16"))

    @Test
    fun an_untrusted_peer_is_itself_whatever_it_claims() {
        assertEquals("203.0.113.9", clientIp("203.0.113.9", "1.2.3.4", trusted))
    }

    @Test
    fun a_trusted_peers_header_is_believed() {
        assertEquals("198.51.100.7", clientIp("172.18.0.2", "198.51.100.7", trusted))
    }

    @Test
    fun a_spoofed_prefix_is_ignored_because_the_walk_starts_from_the_right() {
        assertEquals("198.51.100.7", clientIp("172.18.0.2", "1.2.3.4, 198.51.100.7, 172.18.0.5", trusted))
    }

    @Test
    fun behind_caddy_behind_the_edge_both_hops_must_be_trusted() {
        // Caddy (Compose network) appends the edge's private IP to the client the edge reported.
        val chain = "1.2.3.4, 198.51.100.7, 10.114.0.2"
        assertEquals("198.51.100.7", clientIp("172.18.0.3", chain, trusted + Cidr.parse("10.114.0.2")))
        assertEquals("10.114.0.2", clientIp("172.18.0.3", chain, trusted))
    }

    @Test
    fun out_of_range_octets_are_not_addresses() {
        assertEquals("172.18.0.2", clientIp("172.18.0.2", "999.1.1.1", trusted))
        assertFailsWith<IllegalStateException> { Cidr.parse("localhost") }
    }

    @Test
    fun a_trusted_peer_without_a_header_is_itself() {
        assertEquals("172.18.0.2", clientIp("172.18.0.2", null, trusted))
    }

    @Test
    fun nothing_trusted_means_the_socket_always_wins() {
        assertEquals("127.0.0.1", clientIp("127.0.0.1", "1.2.3.4", emptyList()))
    }

    @Test
    fun cidr_matching_covers_partial_bytes_single_addresses_and_ipv6() {
        assertEquals(true, Cidr.parse("10.0.0.0/9").contains(java.net.InetAddress.getByName("10.127.1.1")))
        assertEquals(false, Cidr.parse("10.0.0.0/9").contains(java.net.InetAddress.getByName("10.128.0.1")))
        assertEquals(true, Cidr.parse("192.0.2.1").contains(java.net.InetAddress.getByName("192.0.2.1")))
        assertEquals(true, Cidr.parse("fd00::/8").contains(java.net.InetAddress.getByName("fd12::1")))
        assertEquals(false, Cidr.parse("fd00::/8").contains(java.net.InetAddress.getByName("10.0.0.1")))
        assertFailsWith<IllegalStateException> { Cidr.parse("10.0.0.0/40") }
    }

    @Test
    fun hostnames_in_the_header_are_never_resolved_and_ipv6_keys_share_a_64() {
        assertEquals("172.18.0.2", clientIp("172.18.0.2", "example.invalid", trusted))
        assertEquals(rateLimitKey("2001:db8:1:2::1"), rateLimitKey("2001:db8:1:2:ffff::9"))
        assertEquals("203.0.113.9", rateLimitKey("203.0.113.9"))
    }
}

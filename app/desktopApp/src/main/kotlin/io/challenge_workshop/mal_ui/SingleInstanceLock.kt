package io.challenge_workshop.mal_ui

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * An OS file lock that lets one desktop process run at a time. The OS drops it when the process dies,
 * so a crash never leaves a stale lock; the file itself is never deleted, since removing it while
 * another process holds it would let a third one lock a different inode.
 *
 * It lives here and not in `:app:shared` because how many processes the app may have is the entry
 * point's decision.
 */
class SingleInstanceLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {

    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        /** The lock, or null when another process (or another holder in this one) has it. */
        fun tryAcquire(file: Path): SingleInstanceLock? {
            file.parent?.let { Files.createDirectories(it) }
            val channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } catch (e: IOException) {
                channel.close()
                throw e
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return SingleInstanceLock(channel, lock)
        }
    }
}

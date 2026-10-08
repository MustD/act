package io.challenge_workshop.mal_ui.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The narrowest thing a Session can be persisted through: three suspending string operations and one flow of other writers' changes.
 *
 * Deliberately hand-rolled rather than a multiplatform settings library — see
 * `docs/adr/0002-hand-rolled-key-value-store.md`. The implementations live in `:app:shared`,
 * not here, because they do filesystem I/O and `:server` depends on `:core` as a plain JVM
 * library; an `expect` here would force a desktop-shaped actual onto a Netty process.
 *
 * Narrow enough that an OS-keychain implementation could be swapped in without touching a
 * caller. Nothing is encrypted at rest on any target today.
 */
interface KeyValueStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun remove(key: String)

    /**
     * The raw value [key] takes whenever **someone else** changes it — `null` when they remove it —
     * and never the caller's own writes and removals. "Someone else" is another browser tab on web;
     * on a target with a single process there is nobody, which is what the default says.
     *
     * Hot and unbuffered: a change made while nothing is collecting is not replayed, so a collector
     * that needs the current value reads it first.
     */
    fun changes(key: String): Flow<String?> = emptyFlow()
}

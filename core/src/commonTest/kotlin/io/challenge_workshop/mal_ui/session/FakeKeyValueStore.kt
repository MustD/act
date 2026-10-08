package io.challenge_workshop.mal_ui.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/** In-memory [KeyValueStore], with the raw map exposed so tests can plant corrupt or stale values. */
class FakeKeyValueStore(
    val entries: MutableMap<String, String> = mutableMapOf(),
) : KeyValueStore {
    override suspend fun read(key: String): String? = entries[key]

    override suspend fun write(key: String, value: String) {
        entries[key] = value
    }

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    private val others = MutableSharedFlow<Pair<String, String?>>(extraBufferCapacity = 16)

    override fun changes(key: String): Flow<String?> = others.filter { it.first == key }.map { it.second }

    /** Another tab changing [key]: updates the map the way the browser would, then tells collectors. */
    suspend fun changeElsewhere(key: String, value: String?) {
        if (value == null) entries.remove(key) else entries[key] = value
        others.emit(key to value)
    }
}

/** A [Clock] that only moves when a test moves it. */
class FakeClock(var current: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)) : Clock {
    override fun now(): Instant = current

    fun advanceBy(millis: Long) {
        current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + millis)
    }
}

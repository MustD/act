package io.challenge_workshop.mal_ui

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SingleInstanceLockTest {
    private val file = Files.createTempDirectory("act-lock").resolve("nested/instance.lock")

    @Test
    fun a_second_acquisition_fails_while_the_first_is_held_and_succeeds_after_release() {
        val first = assertNotNull(SingleInstanceLock.tryAcquire(file))
        assertNull(SingleInstanceLock.tryAcquire(file))

        first.close()

        assertNotNull(SingleInstanceLock.tryAcquire(file)).close()
    }
}

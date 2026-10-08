package io.challenge_workshop.mal_ui.di

import io.challenge_workshop.mal_ui.auth.StartupRedirect
import io.challenge_workshop.mal_ui.session.FileKeyValueStore
import io.challenge_workshop.mal_ui.session.KeyValueStore
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import org.koin.core.context.stopKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class PlatformModuleJvmTest {

    // The repository's scope is `Dispatchers.Main.immediate`, which no JVM test platform provides.
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun initKoin_wires_the_desktop_file_store() {
        val koin = initKoin().koin

        assertIs<FileKeyValueStore>(koin.get<KeyValueStore>())
        // Declared rather than left out: without it `SignIn` does not resolve at all, and the
        // symptom is a crash on launch rather than a missing feature.
        assertSame(StartupRedirect.None, koin.get<StartupRedirect>())

        koin.get<MalSessionRepository>().close()
    }
}

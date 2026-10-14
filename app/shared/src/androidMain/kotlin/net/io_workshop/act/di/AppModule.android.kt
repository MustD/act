package net.io_workshop.act.di

import android.content.Context
import net.io_workshop.act.auth.AndroidStartupRedirect
import net.io_workshop.act.auth.StartupRedirect
import net.io_workshop.act.session.AndroidKeyValueStore
import net.io_workshop.act.session.KeyValueStore
import net.io_workshop.act.session.MAL_STORE_NAMESPACE
import net.io_workshop.act.animelist.CoverPolicy
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<KeyValueStore> {
        // Asserted rather than left to NPE at the first read. The `Context` arrives via
        // `androidContext()` from `Application.onCreate`, which runs before any Activity; Koin
        // started from anywhere else leaves this definition with nothing to open prefs on, and the
        // symptom would otherwise be a crash on the first store access rather than at startup.
        val context = getOrNull<Context>() ?: error(
            "No Android Context in the Koin graph. initKoin() must be called from " +
                "ActApplication.onCreate with androidContext(this).",
        )
        AndroidKeyValueStore(context, MAL_STORE_NAMESPACE)
    }

    // A redirect normally arrives at a *running* Activity and goes to the armed channel. It arrives
    // at a launch only when the process was killed while the user was away on myanimelist.net —
    // which is ordinary on a low-RAM device — and then there is no armed channel left to take it.
    single<StartupRedirect> { AndroidStartupRedirect() }

    // Play's sexual-content policy: Rx covers are censored nudity, so Android never draws an Adult Cover.
    single<CoverPolicy> { CoverPolicy.HideAdult }
}

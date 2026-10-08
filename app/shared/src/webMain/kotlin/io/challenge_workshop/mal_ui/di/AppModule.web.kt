package io.challenge_workshop.mal_ui.di

import io.challenge_workshop.mal_ui.auth.StartupRedirect
import io.challenge_workshop.mal_ui.auth.WebStartupRedirect
import io.challenge_workshop.mal_ui.session.KeyValueStore
import io.challenge_workshop.mal_ui.session.LocalStorageKeyValueStore
import io.challenge_workshop.mal_ui.session.MAL_STORE_NAMESPACE
import io.challenge_workshop.mal_ui.session.SessionStorageKeyValueStore
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The browser actual. */
actual val platformModule: Module = module {
    // Durable: the Session, Layout and Theme survive a closed tab. The Pending Authorization stays
    // tab-scoped. See docs/adr/0007-web-session-in-local-storage.md.
    single<KeyValueStore> { LocalStorageKeyValueStore(MAL_STORE_NAMESPACE) }
    single<KeyValueStore>(named(TAB_SCOPED_STORE)) { SessionStorageKeyValueStore(MAL_STORE_NAMESPACE) }

    // The one target that can be launched holding a redirect: a blocked popup sends the whole tab
    // to MyAnimeList, and what comes back is a new document with a `?code=` in its address bar.
    single<StartupRedirect> { WebStartupRedirect() }
}

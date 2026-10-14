package net.io_workshop.act.di

import net.io_workshop.act.auth.StartupRedirect
import net.io_workshop.act.auth.WebStartupRedirect
import net.io_workshop.act.session.KeyValueStore
import net.io_workshop.act.session.LocalStorageKeyValueStore
import net.io_workshop.act.session.MAL_STORE_NAMESPACE
import net.io_workshop.act.session.SessionStorageKeyValueStore
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

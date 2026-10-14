package net.io_workshop.act.di

import net.io_workshop.act.auth.StartupRedirect
import net.io_workshop.act.session.FileKeyValueStore
import net.io_workshop.act.session.KeyValueStore
import net.io_workshop.act.session.MAL_STORE_NAMESPACE
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<KeyValueStore> { FileKeyValueStore.defaultFor(MAL_STORE_NAMESPACE) }

    // A desktop redirect always arrives at a running process, on the loopback listener.
    single<StartupRedirect> { StartupRedirect.None }
}

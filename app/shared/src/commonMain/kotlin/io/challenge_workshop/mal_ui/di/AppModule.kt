package io.challenge_workshop.mal_ui.di

import io.challenge_workshop.mal_ui.animelist.AnimeListRepository
import io.challenge_workshop.mal_ui.animelist.LayoutPreference
import io.challenge_workshop.mal_ui.animepage.AnimePageRepository
import io.challenge_workshop.mal_ui.auth.SignIn
import io.challenge_workshop.mal_ui.auth.StartupRedirect
import io.challenge_workshop.mal_ui.mal.HttpClientFactory
import io.challenge_workshop.mal_ui.mal.MAL_CLIENT_ID
import io.challenge_workshop.mal_ui.mal.MalAuthConfig
import io.challenge_workshop.mal_ui.session.JsonTokenStore
import io.challenge_workshop.mal_ui.session.MalSessionRepository
import io.challenge_workshop.mal_ui.session.SessionControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Everything shared by every target.
 *
 * `:core` stays Koin-free — no annotations, no module declarations, no `koin-core` dependency — so
 * `:server` and `./gradlew :core:allTests` are untouched by dependency injection existing at all.
 */
val appModule: Module = module {
    single<Clock> { Clock.System }

    // Lenient because MAL adds fields, and `ignoreUnknownKeys` is what stops a MAL-side addition
    // turning a stored Session into a corrupt blob on the next launch.
    single { Json { ignoreUnknownKeys = true; isLenient = true } }

    single { JsonTokenStore(kv = get(), json = get(), clock = get()) }

    single { HttpClientFactory.Default }

    // A `single`, and it matters: two repositories would mean two Ktor `AuthTokenHolder` caches over
    // one store, and therefore a refresh race that the plugin's own mutex cannot see.
    single {
        MalSessionRepository(
            store = get(),
            clock = get(),
            // The build-time default only. A Client ID the user typed is remembered per device and
            // replaces this in `restore()`; with neither, the sign-in screen prompts for one. This is
            // the only place the generated constant is read — `:core` keeps `clientId` a required
            // parameter so its own tests cannot pick up whatever this machine's build was configured
            // with.
            initialConfig = MalAuthConfig(clientId = MAL_CLIENT_ID),
            clientFactory = get(),
        )
    }

    // A `single`, matching the lifetime of the store it reads: the Layout is a device preference,
    // not a screen's state, and it is one of the Screen State's six inputs — so it has to outlive
    // every composition that reads it, and be the same instance for the source and the actions.
    //
    // `Dispatchers.Main.immediate` confines the preference's `chosen` guard to one thread, and runs a
    // Layout switch inside the click that asked for it. A scope of its own rather than a
    // `viewModelScope`, because a startup read cancelled by a screen going away would leave the
    // shipped default on screen for the rest of the launch.
    single {
        LayoutPreference(
            store = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

    // A `single` with a scope of its own, like the Anime List: a Sign-in lasts until it ends, and
    // nothing about a screen or composition going away is one of the ways it ends. `Main.immediate` so
    // that `start` runs inside the click that asked for it — a web popup's user activation depends on it.
    // Constructing it restores the Session, so `App()` resolving it is what starts the app.
    single {
        SignIn(
            repository = get(),
            startupRedirect = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

    // A `single` with a scope of its own, like `SignIn`, so an operation outlives a screen going away.
    // `Main.immediate` runs one inside the click that asked for it.
    single {
        SessionControls(
            repository = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

    // A `single`, like the Session it watches: it builds one list per Session and discards it when the
    // Session ends, so that lifetime is its own interface rather than a consequence of scoping here.
    // It takes the Session's repository rather than a client of its own, because every page must
    // ride the one authenticated `HttpClient` that owns refresh.
    //
    // `Dispatchers.Main.immediate`, as for the Layout above: it confines the pager to one thread and
    // starts an operation inside the click that asked for it.
    single {
        AnimeListRepository(
            session = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

    // A `single` for the same reason as the Anime List beside it: the open Anime Pages last one
    // Session, by the repository's own rule, and the Anime List is the other half of what a fetched
    // page writes to.
    single {
        AnimePageRepository(
            session = get(),
            animeList = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }
}

/**
 * The per-target half of the graph: whatever `KeyValueStore` this platform can actually open, and
 * whichever [StartupRedirect] it can be launched with.
 *
 * A `Module` rather than a `expect fun platformKeyValueStore(namespace)` because the Android
 * implementation needs a `Context`, and a Koin definition is the one place that reliably has one.
 */
expect val platformModule: Module

/**
 * Starts Koin. Called by every entry point, so they cannot drift.
 *
 * @param extra additional modules, used by tests to override a binding.
 * @param declaration runs before the modules are loaded. Android uses it for `androidContext(this)`,
 * which has to happen before anything resolves a `Context`.
 */
fun initKoin(
    extra: List<Module> = emptyList(),
    declaration: KoinApplication.() -> Unit = {},
): KoinApplication = startKoin {
    declaration()
    modules(listOf(appModule, platformModule) + extra)
}

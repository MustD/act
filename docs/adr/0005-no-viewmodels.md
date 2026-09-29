# No ViewModels: the Session's operations are process-scoped modules in `:core`

Status: accepted

The Sign-in (arming a Redirect Capture, the wait on myanimelist.net, Paste-the-code, the startup redirect) is owned by
`SignIn`, and the signed-in screen's one-shot operations (sign-out, the debug panel) by `SessionControls`. Both are
process-scoped `single`s in `:core` with their own `Main.immediate` scope, like `AnimeListRepository` and
`LayoutPreference`. **There is no `MalSessionViewModel`, and a ViewModel should not be reintroduced** to hold any of
this. A reader coming from Android will expect one. This file is here so they don't add it back.

## Why

- **The orchestration is the part that can be wrong the same way on four Targets**, so it belongs where
  `:core:allTests` runs it: ADR-0004's argument applied one step further. `AuthRedirectChannel` never needed Compose;
  only `rememberAuthRedirectChannel()` does, and that stays in `:app:shared`.
- **A ViewModel's lifetime was the wrong rule for a Sign-in.** A Sign-in ends on its own outcome, on the user backing
  out, on a new Sign-in, or on the Session leaving `Authorizing`. None of those is "the ViewModelStore was cleared",
  which is what `onCleared` tied it to. Making the lifetime part of the module's interface is what the Anime List
  already did (ADR-0004's 2026-09-24 note).
- **Once the Sign-in moved, what was left failed the deletion test.** The ViewModel would have held two pass-through
  flows and some form text, so deleting it concentrated nothing.

## Considered options

- **A thin ViewModel handing `viewModelScope` to `SignIn`.** Rejected. It keeps a module whose only job is a scope, and
  it quietly reinstates `onCleared` as a way to end a Sign-in.
- **One module for everything the ViewModel did.** Rejected. Sharing one `busy`/`error` pair between the Sign-in and
  sign-out is how a failed sign-out came to be shown under "Sign-in failed", and how one job's `finally` cleared
  another's spinner. The two have different lifetimes: a Sign-in lasts while the user is away, and an operation is one
  call.
- **Cancelling the Sign-in when the composition that owns the channel is disposed.** Rejected. It would cancel on every
  Android configuration change. Cancelling is non-destructive (the Pending Authorization survives), but it would still
  put the user back on the button for rotating their phone.

## Consequences

- Nothing ends a Sign-in because a screen went away. A channel whose composition is gone can therefore be awaited by a
  live Sign-in; see `.scratch/sign-in-module/issues/08`.
- Web's popup user activation no longer rests on `viewModelScope` being `Main.immediate`. `SignIn.start` launches
  `UNDISPATCHED`, and `PopupUserActivationTest` pins it.
- Form text now survives a configuration change and is still lost with the process. That's acceptable: it was always
  meant to be ephemeral.

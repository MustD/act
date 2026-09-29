package io.challenge_workshop.mal_ui.auth

/**
 * Android's [StartupRedirect]: a redirect that reached `MainActivity` before there was anything to
 * hand it to.
 *
 * That is process death while the user was away approving on myanimelist.net — an ordinary outcome
 * of being parked behind a browser on a low-RAM device, not a theoretical one, and arguably the top
 * cause of "OAuth works on my Pixel and fails on cheap phones". The redirect relaunches the app, so
 * the process that receives it has no armed [IntentRedirectChannel], no sign-in in flight and no
 * `state` in memory to check against. What it does have is the Pending Authorization the repository
 * persisted before the browser ever opened, which is the whole reason that record exists.
 *
 * Unfiltered by `state`, unlike [IntentRedirectChannel]: there is none in this process to compare
 * against, so that check happens where the Pending Authorization is — in
 * `MalSessionRepository.completeAuthorization`.
 */
internal class AndroidStartupRedirect(
    private val inbox: AuthRedirectInbox = AuthRedirectInbox.Shared,
) : StartupRedirect {

    /**
     * Takes the launch redirect and marks it [LaunchRedirect.reportIfStale] = false.
     *
     * A launch Intent is not a user action: it stays on the `ActivityRecord` of an activity that a
     * redirect started, so the *system* re-delivers it on every later relaunch of that task — days
     * after the login it belongs to finished. `SignIn` completes it only while the Session is
     * `Authorizing` and discards it silently otherwise, rather than putting "there is no sign-in in
     * progress" over a Session that is working perfectly. This class no longer reads the store to
     * decide that: the Pending Authorization has one reader, `MalSessionRepository`.
     *
     * Web marks its equivalent the other way, because a `?code=` in the address bar always means the
     * user *just* came back. A redirect that arrives while this app is running still goes through
     * [IntentRedirectChannel] and still reports every error it produces.
     */
    override suspend fun consume(): LaunchRedirect? =
        inbox.claimLaunchRedirect()?.let { LaunchRedirect(it, reportIfStale = false) }
}

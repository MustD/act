package net.io_workshop.act.session

/**
 * Names the store on every target: the `SharedPreferences` file on Android, the directory in the OS state
 * folder on desktop (see `FileKeyValueStore.defaultPathFor`), the key prefix in both `localStorage` (Session,
 * Layout, Theme) and `sessionStorage` (Pending Authorization) on web.
 *
 * Unversioned on purpose — the *keys* carry the version (`mal.session.v1`), so a format change is
 * a clean re-login without orphaning a whole namespace.
 */
const val MAL_STORE_NAMESPACE: String = "net.io_workshop.act"

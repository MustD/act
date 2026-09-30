# Probe: `my_list_status` reads and writes

Companion to ticket 01 of the Anime Page feature (`.scratch/anime-page/issues/01-probe-list-status-api.md`), answering
the Further Notes of `.scratch/anime-page/spec.md` before the save queue (ticket 06), the automatic rules (07) and
Related Anime (08) are built on them. Run on **2026-09-30** against the test account `io-mai-ui-test`, with the access
token of a signed-in desktop Session, straight at `api.myanimelist.net` with `curl`.

Every write went to anime that were **not** on the account's list — Trigun (6, 26 eps), Neon Genesis Evangelion (30,
26 eps), Cowboy Bebop: Tengoku no Tobira (5, 1 ep), One Piece (21, total unknown) — and each was deleted afterwards. The
two entries the account already had (Frieren 52991, Frieren 2nd Season 59978) were only read; the list was re-read at
the end and both came back with their original `updated_at`.

## The answers

| Question                                    | Answer                                                                                     |
|---------------------------------------------|--------------------------------------------------------------------------------------------|
| Does the API apply the automatic rules?     | **No, none of them.** The app must apply every one itself.                                  |
| Can a PATCH clear a date, and how?          | **Yes: an empty value**, `start_date=` / `finish_date=`. `0000-00-00` works too.            |
| What does a partial date look like?         | **`"2024"`** and **`"2024-03"`** — the unknown parts are dropped, not zeroed.               |
| Do `related_anime` nodes carry it?          | **Yes**, with `fields=related_anime{my_list_status}`. No follow-up request is needed.       |
| Out-of-range `num_watched_episodes`?        | **200, clamped** to `0…num_episodes`. Unbounded when the total is unknown.                  |
| Out-of-range `score`?                       | **400** `{"message":"invalid score","error":"bad_request"}`.                                |
| PATCH on an anime not on the list?          | **200, the entry is created**; same response shape as an update.                           |

## What came back

### The automatic rules: MAL applies none

Each PATCH sent only the field under test, and the response and a follow-up `GET` agreed every time:

| Sent                                                     | Came back                                                  |
|----------------------------------------------------------|------------------------------------------------------------|
| `num_watched_episodes=1` on a Plan to Watch entry at 0   | still `plan_to_watch`, no `start_date`                     |
| `num_watched_episodes=26` (of 26) on Plan to Watch       | still `plan_to_watch`, 26/26, no `finish_date`             |
| `num_watched_episodes=26` (of 26) on Watching at 25      | still `watching`, no `finish_date`                         |
| `status=completed` on Watching at 0 of 26                | `completed` with `num_episodes_watched: 0`                 |
| add with `status=watching`                               | no `start_date`                                            |
| add with `status=completed` (a 1-episode movie)          | `num_episodes_watched: 0`, no `finish_date`                |

So the API does **no** consistency checking between status and progress — Plan to Watch at 26/26 is stored as sent —
and nothing on the server side would double-apply a rule the app applies. Every row of the spec's "Automatic rules"
section and its add-to-list table stands as written, and all of it is the app's job.

### Dates

`start_date` and `finish_date` behave identically. Sent form-encoded, read back from both the PATCH response and a
`GET`:

| Sent                       | Status | Stored as                |
|----------------------------|--------|--------------------------|
| `2024-03-05`               | 200    | `"2024-03-05"`           |
| `2024-03`                  | 200    | `"2024-03"`              |
| `2024`                     | 200    | `"2024"`                 |
| `2024-03-00`               | 200    | `"2024-03"`              |
| `2024-00-00`               | 200    | `"2024"`                 |
| *(empty)*                  | 200    | **cleared**              |
| `0000-00-00`               | 200    | **cleared**              |
| `2024-02-30`               | 200    | **cleared** — silently   |
| `03/05/2024`, `null`       | 400    | unchanged                |

- **A date that isn't set is an absent key**, not `null` and not `""` — in the PATCH response, in `GET /anime/{id}`'s
  `my_list_status`, and in the list endpoint alike. The wire type needs `String? = null` with the key simply missing.
- **Leaving a date out of a PATCH keeps it.** Only a present-but-empty field clears.
- **An impossible calendar date clears the field with a 200.** Nothing warns. The Material 3 `DatePicker` can't produce
  one, but the PATCH client should only ever format a real `LocalDate`, never pass a string through.
- The 400 for an unparseable date is `{"error":"bad_request"}` with no `message`, unlike the other 400s below.

### Related Anime

`GET /anime/52991?fields=related_anime{my_list_status}` — Frieren, whose sequel is on the test account's list:

```json
{"node":{"id":59978,"title":"Sousou no Frieren 2nd Season","main_picture":{…},
         "my_list_status":{"status":"watching","score":0,"num_episodes_watched":2,"is_rewatching":false,
                           "updated_at":"2026-08-27T07:59:19+00:00","start_date":"2026-08-27"}},
 "relation_type":"sequel","relation_type_formatted":"Sequel"}
```

- `related_anime{my_list_status}` and `related_anime{node{my_list_status}}` return the same thing: the braces after
  `related_anime` select fields of its `node`.
- An anime **not** on the list simply has no `my_list_status` key on its node.
- **`my_list_status` takes no sub-field selection, and trying one misfires quietly.** `my_list_status{start_date}` is
  not read as "these fields of the list status" — the `start_date` lands on the *node* instead (the anime's air date,
  `"2023-09-29"`), while `my_list_status` comes back whole, dates included. Top-level `fields=my_list_status` likewise
  already includes `start_date` and `finish_date` when they are set; nothing extra has to be asked for.
- `relation_type_formatted` is present without being requested, and `related_anime` alone returns id, title and
  `main_picture` per node — everything the Related Anime row needs.

### Out-of-range values

On Trigun (26 episodes):

| Sent                              | Status | Result                                                        |
|-----------------------------------|--------|---------------------------------------------------------------|
| `num_watched_episodes=27` from 10 | 200    | **26** — clamped to the total                                 |
| `num_watched_episodes=500`        | 200    | 26                                                            |
| `num_watched_episodes=-1`         | 200    | **0**                                                         |
| `num_watched_episodes=abc`        | 400    | `{"message":"invalid num_watched_episodes","error":"bad_request"}` |
| `score=11`, `score=-1`            | 400    | `{"message":"invalid score","error":"bad_request"}`           |
| `score=0`                         | 200    | 0 — "No score"                                                |
| `score=7.5`                       | 200    | **8** — rounded                                               |
| `status=bogus`                    | 400    | `{"message":"invalid status","error":"bad_request"}`          |

With the total unknown (One Piece, `num_episodes: 0`), `num_watched_episodes=5000` is stored as 5000 — there is no
upper bound to clamp to, which matches the spec's "no upper limit when the total is 0".

### Creating, and the PATCH response

- **PATCH on an anime that is not on the list creates the entry: 200**, not 201, with the same body as an update. There
  is no separate "add" call.
- A PATCH for an anime id that does not exist is **404** `{"message":"","error":"not_found"}`.
- The response is the **whole** List Entry after the write, not an echo of what was sent, and it carries fields the
  `GET` doesn't: `priority`, `num_times_rewatched`, `rewatch_value`, `tags`, `comments`. The wire type should ignore
  unknown keys.
- **The request and response name progress differently**: `num_watched_episodes` in the form body,
  `num_episodes_watched` in every response. A client written by symmetry will send a field MAL ignores — it answers
  200 with the value unchanged, not 400.
- A PATCH with **no fields** is a 200 that changes nothing except `updated_at`. The save queue's "send the target only if
  it differs from what MAL confirmed" is what keeps those from happening.

## What it means for the design

- **Ticket 07 builds every rule**, both the edit rules and the add table. There is nothing to subtract.
- **Ticket 06's "MAL's response is the new confirmed value" is exactly right, and necessary.** Because progress is
  clamped and scores rounded rather than refused, the confirmed value can differ from the target without any error. The
  queue must take the response's values, not assume its request went through verbatim.
- **Clearing a date is sending the field empty.** The client's form encoding needs three states per date — absent (keep),
  empty (clear), `yyyy-MM-dd` (set) — so a plain `LocalDate?` parameter isn't enough to express the PATCH.
- **Partial dates are real** and come in two shapes, `yyyy` and `yyyy-MM`. The domain type must hold them without
  failing to parse; the spec's "shown as it is, and picking a date replaces it" stands.
- **Ticket 08 gets "on your list" from the page's own fetch**: `related_anime{my_list_status}` in the same
  `GET /anime/{id}`. No follow-up request, and the mark is not dropped.
- **Error display has a `message` to show** for most 400s, but not all — the date 400 has none, so the fallback copy
  must not depend on it.

## Re-running it

Needs an access token for an account whose list can be touched. The desktop Session stores one in
`~/.local/state/io.challenge_workshop.mal_ui/store.json` under `mal.session.v1` → `tokens.access_token`; pick anime that
are not on that list and `DELETE /v2/anime/{id}/my_list_status` them afterwards (a DELETE for an anime not on the list
is also a 200, so the cleanup is safe to repeat).

```bash
T=…   # the access token
curl -s -X PATCH -H "Authorization: Bearer $T" https://api.myanimelist.net/v2/anime/6/my_list_status \
     -d status=plan_to_watch
curl -s -X PATCH -H "Authorization: Bearer $T" https://api.myanimelist.net/v2/anime/6/my_list_status \
     --data-urlencode start_date=
curl -s -g -H "Authorization: Bearer $T" 'https://api.myanimelist.net/v2/anime/52991?fields=related_anime{my_list_status}'
curl -s -X DELETE -H "Authorization: Bearer $T" https://api.myanimelist.net/v2/anime/6/my_list_status
```

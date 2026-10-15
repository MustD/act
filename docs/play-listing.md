# Google Play listing and Console answers

Draft for the owner to edit. Source of decisions: `.scratch/play-release/spec.md`. Vocabulary: `CONTEXT.md`.

## Store listing (English only)

**Title** (27/30): `ACT: Anime Control Terminal`

**Short description** (≤80):

```
Browse and manage your MyAnimeList anime list in cards or a compact list.
```

**Full description** (≤4000):

```
ACT is a fast, tidy client for your MyAnimeList anime list.

Sign in with your MyAnimeList account (your password is only ever typed on myanimelist.net) and your list is there:

• Two layouts: big cover Cards or a dense List — switch any time
• Filter and sort by Watch Status, as MyAnimeList itself does
• Anime Pages with synopsis, scores, studios, genres and Related Anime
• Edit your list entries: Watch Status, episodes watched, score
• Light, dark or system theme
• Covers MyAnimeList marks as adult are replaced by a neutral "18+" placeholder in this Android app

No ads. No analytics. No tracking. ACT talks to MyAnimeList on your behalf and keeps nothing on any server of its own.

Unofficial — not affiliated with or endorsed by MyAnimeList. Anime data provided by MyAnimeList.net.

Also available on the web and desktop.
```

Not mentioned, deliberately: Ko-fi, any MAL logo or mark as a name.

**Privacy policy URL:** `https://act.io-workshop.net/privacy`

**Category:** Entertainment (alternative: Tools). **Contact email:** the owner's choice. **Language:** English.

## Data safety

| Question | Answer |
|---|---|
| Collects or shares user data | Collects; shares nothing |
| Personal info → User IDs | Collected (MAL account identity), not shared, required, purpose: app functionality |
| App activity → Other actions | Collected (list edits sent to MAL), not shared, required, purpose: app functionality |
| Everything else (location, contacts, financial, photos, device IDs, …) | Not collected |
| Encrypted in transit | Yes |
| Users can request deletion | Yes — through their MAL account; ACT stores nothing server-side |
| Account creation in app | No (sign-in to an existing MAL account), so the account-deletion requirement does not apply |
| Independent security review | No |

## Content rating questionnaire (IARC)

Category: **Utility / reference** (not a game, not social, no user-generated content).

- Violence, blood, fear, profanity, drugs, gambling: **No**.
- Sexual content / nudity: **Yes — third-party content only.** The app lists anime from MyAnimeList; cover images
  MyAnimeList marks adult (rated Rx, not `nsfw: white`, or unmarked) are never drawn on Android (placeholder, no reveal),
  but titles and synopses of such anime are text MyAnimeList supplies and may reference mature themes.
- User-generated content / user interaction: **No** (ACT has no user-to-user features).
- Shares location / purchases digital goods / unrestricted web access: **No**.

Expect roughly **Teen**; if the questionnaire lands higher, answer honestly rather than adjusting the answers.

## Target audience and content

**Lowered from 18+ only** (the spec's earlier decision), because Android no longer draws Adult Covers (ticket 07).

- **Target age groups:** `16–17` and `18+`. Do not select any group under 13 — that triggers the Families policy, and
  MAL itself requires 13+.
- Widening to `13–15` is possible; the residual risk is adult-themed synopsis text and anime titles, which MAL supplies
  and ACT cannot filter without changing what the list shows.
- **Appeals to children:** No.
- **Ads:** No.

## Screenshot shot list (phone, real device, signed in with a clean list)

1. Cards Layout
2. List Layout
3. Anime Page
4. Editing a List Entry
5. Welcome Screen

**No shot may show an Adult Cover placeholder** — choose a list or a filter with none (and no real adult-themed title).
Assets: icon `docs/play-assets/store-icon-512.png`, feature graphic `docs/play-assets/feature-graphic-1024x500.png`.

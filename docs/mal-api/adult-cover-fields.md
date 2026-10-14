# Cover marks: `rating` and `nsfw`

Ticket 07 of play-release. An Adult Cover (`CONTEXT.md`) is classified from two node fields, `rating` and `nsfw`.

| Request                         | `fields` carries                                   |
|---------------------------------|----------------------------------------------------|
| `GET /users/@me/animelist`      | `rating,nsfw` on the node                          |
| `GET /anime/{id}`               | `rating,nsfw`, and `related_anime{my_list_status,rating,nsfw}` |

**Not probed live.** No signed-in session was available when this was written, so that `related_anime` nodes
actually return `rating` and `nsfw` is unconfirmed (nested node fields are known to work for `my_list_status`,
`list-status-probe.md`). The code fails closed: if MAL omits them, every Related Anime is an Adult Cover and Android shows
the placeholder for all of them. Probe with a token before release and record the answer here:

```
curl -H "Authorization: Bearer $TOKEN" \
  'https://api.myanimelist.net/v2/anime/1?fields=related_anime{my_list_status,rating,nsfw}'
```

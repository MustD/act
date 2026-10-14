package net.io_workshop.act.animelist

/**
 * Whether an anime's cover is an **Adult Cover** (`CONTEXT.md`): MAL rates it `rx`, or does not mark it
 * `white` for `nsfw`, or sent either mark as nothing at all.
 *
 * Fails closed on purpose: MAL omits a field it has no value for, and a cover that cannot be shown to
 * be safe is treated as unsafe. `r+` covers are ordinary key art and `nsfw: white`, so they pass.
 */
fun isAdultCover(rating: String?, nsfw: String?): Boolean =
    rating == null || nsfw == null || rating == "rx" || nsfw != "white"

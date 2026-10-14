@file:OptIn(ExperimentalTestApi::class)

package net.io_workshop.act

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import net.io_workshop.act.animelist.ADULT_COVER_PLACEHOLDER_TAG
import net.io_workshop.act.animelist.AnimeCoverBox
import net.io_workshop.act.animelist.CoverPolicy
import net.io_workshop.act.animelist.LocalCoverPolicy
import net.io_workshop.act.theme.ActTheme
import kotlin.test.Test

class AdultCoverTest {
    private fun render(policy: CoverPolicy, adult: Boolean, check: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalCoverPolicy provides policy) {
                    ActTheme(dark = true) { AnimeCoverBox("Title", url = null, adult = adult) }
                }
            }
            check()
        }

    @Test
    fun an_adult_cover_is_replaced_where_the_policy_hides() =
        render(CoverPolicy.HideAdult, adult = true) { onNodeWithTag(ADULT_COVER_PLACEHOLDER_TAG).assertIsDisplayed() }

    @Test
    fun an_adult_cover_is_drawn_where_the_policy_shows() =
        render(CoverPolicy.ShowAll, adult = true) { onNodeWithTag(ADULT_COVER_PLACEHOLDER_TAG).assertDoesNotExist() }

    @Test
    fun an_ordinary_cover_is_never_replaced() =
        render(CoverPolicy.HideAdult, adult = false) { onNodeWithTag(ADULT_COVER_PLACEHOLDER_TAG).assertDoesNotExist() }
}

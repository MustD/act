package net.io_workshop.act.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext

internal tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

/**
 * `enableEdgeToEdge()` with no arguments reads the *system* theme, which is the wrong one whenever the
 * in-app Theme disagrees with the phone. Passing the resolved value through `SystemBarStyle.auto`'s
 * detector makes the icon colour follow it instead.
 */
@Composable
actual fun SystemBarsFollowTheme(dark: Boolean) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    SideEffect {
        val style = SystemBarStyle.auto(
            lightScrim = android.graphics.Color.TRANSPARENT,
            darkScrim = android.graphics.Color.TRANSPARENT,
            detectDarkMode = { dark },
        )
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

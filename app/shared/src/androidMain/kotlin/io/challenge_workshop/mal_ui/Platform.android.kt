package io.challenge_workshop.mal_ui

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val hasKeyboard: Boolean = false
}

actual fun getPlatform(): Platform = AndroidPlatform()
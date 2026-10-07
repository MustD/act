package io.challenge_workshop.mal_ui

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val hasKeyboard: Boolean = true
}

actual fun getPlatform(): Platform = WasmPlatform()

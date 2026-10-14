package net.io_workshop.act

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val hasKeyboard: Boolean = true
}

actual fun getPlatform(): Platform = WasmPlatform()

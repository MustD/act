package io.challenge_workshop.mal_ui

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val hasKeyboard: Boolean = true
}

actual fun getPlatform(): Platform = JVMPlatform()

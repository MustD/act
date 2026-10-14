package net.io_workshop.act

interface Platform {
    val name: String

    /** Whether a physical keyboard is the expected input, so key hints are worth the space. */
    val hasKeyboard: Boolean
}

expect fun getPlatform(): Platform

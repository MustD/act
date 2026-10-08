package io.challenge_workshop.mal_ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.challenge_workshop.mal_ui.di.initKoin
import io.challenge_workshop.mal_ui.session.FileKeyValueStore
import io.challenge_workshop.mal_ui.session.MAL_STORE_NAMESPACE
import java.awt.GraphicsEnvironment
import javax.swing.JOptionPane
import kotlin.system.exitProcess

private const val ALREADY_RUNNING = "ACT is already running."

fun main() {
    // Before Koin, the window and anything that can bind the loopback port: a second process must
    // not get as far as disturbing the first one's sign-in.
    val lockFile = FileKeyValueStore.defaultPathFor(MAL_STORE_NAMESPACE).resolveSibling("instance.lock")
    val lock = SingleInstanceLock.tryAcquire(lockFile)
    if (lock == null) {
        refuseSecondLaunch()
        exitProcess(1)
    }
    // Held until the process ends; the OS releases it then, however it ends.
    Runtime.getRuntime().addShutdownHook(Thread { lock.close() })

    initKoin()
    ui()
}

private fun refuseSecondLaunch() {
    if (GraphicsEnvironment.isHeadless()) {
        System.err.println("$ALREADY_RUNNING Close the running instance before starting another.")
    } else {
        JOptionPane.showMessageDialog(null, ALREADY_RUNNING, "ACT", JOptionPane.INFORMATION_MESSAGE)
    }
}

private fun ui() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ACT",
    ) {
        App()
    }
}

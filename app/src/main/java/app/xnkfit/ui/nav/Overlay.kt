package app.xnkfit.ui.nav

/** Full screens opened over the bottom bar (Saya → Pengaturan → Data, Bantuan, Privasi; Kartu saya). */
enum class Overlay { CARDS, SETTINGS, DATA, HELP, PRIVACY }

/**
 * A back stack of [Overlay]s, kept as plain text so `rememberSaveable` can save it. Empty = the bottom bar shows.
 * Pure Kotlin so a JVM test covers it.
 */
object OverlayStack {
    fun parse(saved: String): List<Overlay> = saved.split(',').mapNotNull { n -> Overlay.entries.firstOrNull { it.name == n } }

    fun save(stack: List<Overlay>): String = stack.joinToString(",") { it.name }

    /** Opening a screen already on the stack goes back to it instead of stacking a second copy. */
    fun open(stack: List<Overlay>, screen: Overlay): List<Overlay> =
        if (screen in stack) stack.takeWhile { it != screen } + screen else stack + screen

    fun back(stack: List<Overlay>): List<Overlay> = stack.dropLast(1)
}

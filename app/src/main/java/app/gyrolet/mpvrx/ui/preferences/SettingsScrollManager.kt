package app.gyrolet.mpvrx.ui.preferences

/**
 * Holds a pending scroll index when navigating from settings search to a specific preference.
 * The destination screen reads and clears this value on entry.
 */
object SettingsScrollManager {
    var pendingScrollIndex: Int? = null

    fun consumeScrollIndex(): Int? {
        val index = pendingScrollIndex
        pendingScrollIndex = null
        return index
    }
}

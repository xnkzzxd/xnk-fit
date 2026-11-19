package app.xnkfit.ui.pengaturan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.privacy.DataWiper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The typed word must match, case and surrounding spaces aside (same rule as the restore word, B-5). */
fun deleteWordMatches(typed: String, word: String): Boolean = typed.trim().equals(word, ignoreCase = true)

data class DeleteUi(
    val open: Boolean = false,
    val typed: String = "",
    val busy: Boolean = false,
    val failed: Boolean = false,
)

/** "Hapus semua data" (PRD B-7): a typed confirmation, then everything on the phone goes. */
class DeleteDataViewModel(
    private val wiper: DataWiper,
    /** The word to type, from the string resource. */
    private val word: () -> String,
    /** Called once after a complete wipe; the app restarts into onboarding. */
    private val onWiped: () -> Unit,
) : ViewModel() {
    private val _ui = MutableStateFlow(DeleteUi())
    val ui: StateFlow<DeleteUi> = _ui

    fun open() = _ui.update { DeleteUi(open = true) }

    fun cancel() = _ui.update { if (it.busy) it else DeleteUi() }

    fun type(text: String) = _ui.update { if (it.busy) it else it.copy(typed = text, failed = false) }

    fun confirm() {
        val s = _ui.value
        if (s.busy || !deleteWordMatches(s.typed, word())) return
        _ui.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            val ok = try { wiper.wipe(); true } catch (e: Exception) { false }
            if (ok) onWiped() else _ui.update { it.copy(busy = false, failed = true) }
        }
    }
}

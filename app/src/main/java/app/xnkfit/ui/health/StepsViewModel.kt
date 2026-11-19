package app.xnkfit.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.health.StepsAskedStore
import app.xnkfit.data.health.StepsSource
import app.xnkfit.domain.health.HealthAvailability
import app.xnkfit.domain.health.StepsCard
import app.xnkfit.domain.health.stepsCard
import app.xnkfit.domain.health.weekWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Steps card state (L-15). Starts hidden; [refresh] runs when the screen opens and after the system dialog answers. */
class StepsViewModel(
    private val source: StepsSource,
    private val asked: StepsAskedStore,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _card = MutableStateFlow<StepsCard>(StepsCard.Hidden)
    val card: StateFlow<StepsCard> = _card.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _card.value = load() }
    }

    /** The user answered the system dialog, whatever the answer: never ask again by ourselves. */
    fun onPermissionAnswered() {
        viewModelScope.launch {
            asked.markAsked()
            _card.value = load()
        }
    }

    private suspend fun load(): StepsCard {
        val availability = source.availability()
        if (availability != HealthAvailability.AVAILABLE) return StepsCard.Hidden
        val day = today()
        return try {
            val granted = source.hasPermission()
            val days = if (granted) weekWindow(day).let { source.read(it.start, it.endInclusive) } else emptyList()
            stepsCard(availability, granted, asked.asked(), days, day)
        } catch (e: Exception) { // Health Connect can fail or be updating: the card simply stays hidden
            if (e is kotlinx.coroutines.CancellationException) throw e
            StepsCard.Hidden
        }
    }
}

package app.xnkfit.data.health

import app.xnkfit.domain.health.DaySteps
import app.xnkfit.domain.health.HealthAvailability
import java.time.LocalDate

/** Where steps come from. The real one is Health Connect (read only, READ_STEPS); tests use a fake. */
interface StepsSource {
    fun availability(): HealthAvailability
    suspend fun hasPermission(): Boolean
    suspend fun read(from: LocalDate, to: LocalDate): List<DaySteps>
}

package app.xnkfit.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.xnkfit.domain.health.DaySteps
import app.xnkfit.domain.health.HealthAvailability
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/** Health Connect steps, read only (L-15). Not run on a device in the sandbox; the logic around it is tested with a fake [StepsSource]. */
class HealthConnectSteps(private val context: Context) : StepsSource {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
        else -> HealthAvailability.MISSING
    }

    override suspend fun hasPermission(): Boolean = PERMISSION in client.permissionController.getGrantedPermissions()

    override suspend fun read(from: LocalDate, to: LocalDate): List<DaySteps> {
        val request = AggregateGroupByPeriodRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atTime(LocalTime.MIDNIGHT)),
            timeRangeSlicer = Period.ofDays(1),
        )
        return client.aggregateGroupByPeriod(request).map { DaySteps(it.startTime.toLocalDate(), it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
    }

    companion object {
        /** The only Health Connect permission the app asks for. */
        val PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)
        val PERMISSIONS: Set<String> = setOf(PERMISSION)
    }
}

package app.xnkfit.data.progress

import androidx.room.withTransaction
import app.xnkfit.data.content.ContentParser
import app.xnkfit.data.db.BadgeEntity
import app.xnkfit.data.db.JourneyMomentEntity
import app.xnkfit.data.db.MeasureEntity
import app.xnkfit.data.db.PointsEventEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.progress.BadgeDefinition
import app.xnkfit.domain.progress.Clock
import app.xnkfit.domain.progress.LevelProgress
import app.xnkfit.domain.progress.ProgressData
import app.xnkfit.domain.progress.StreakResult
import app.xnkfit.domain.progress.activeDays
import app.xnkfit.domain.progress.badgesToAward
import app.xnkfit.domain.progress.computeStreak
import app.xnkfit.domain.progress.countPersonalRecords
import app.xnkfit.domain.progress.derivePointEvents
import app.xnkfit.domain.progress.levelProgress
import java.time.LocalDate
import java.time.ZoneId

/** Journey moment kinds written by the app (Design §3 `journey_moment.kind`). The UI turns them into text. */
object MomentKind {
    const val BADGE = "badge"
    const val LEVEL = "level"
    const val NOTE = "note"
}

/** One badge on the screen. [earnedAt] is null while it is locked. */
data class BadgeState(val info: BadgeInfo, val earnedAt: Long?)

data class ProgressSummary(
    val points: Int,
    val level: LevelProgress,
    val streak: StreakResult,
    val finishedWorkouts: Int,
    val personalRecords: Int,
    val badges: List<BadgeState>,
    val moments: List<JourneyMomentEntity>,
)

/**
 * Reads the Room tables, runs the pure rules in `domain.progress` and writes the result back:
 * points (stable ids, so a recount is a no-op), badges (earned once) and journey moments.
 * Everything stays on the phone (PV-1).
 */
class ProgressRepository(
    private val db: XnkDatabase,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = db.progressDao()

    private suspend fun catalog(): List<BadgeInfo> {
        val body = db.contentDao().doc(ContentParser.DOC_BADGES)?.bodyJson ?: return emptyList()
        return BadgeCatalog.parse(body)
    }

    private suspend fun loadData(): ProgressData = ProgressData(
        workouts = dao.finishedWorkouts(),
        sets = dao.doneSets(),
        activities = dao.activityRows(),
        foodLogs = dao.foodLogRows(),
        measures = dao.measureRows(),
        plans = dao.planRows(),
        photoCount = dao.photoCount(),
        points = dao.pointRows(),
    )

    /**
     * Brings points, badges and moments up to date and returns the badges earned just now, so the screen can
     * say the celebration message once. Safe to call any number of times.
     */
    suspend fun refresh(): List<BadgeInfo> = db.withTransaction {
        val clock = Clock(zone(), today())
        val t = now()
        val before = loadData()
        val levelBefore = levelProgress(before.points.sumOf { it.points }).level

        dao.addPointsIfNew(
            derivePointEvents(before, clock).map {
                PointsEventEntity(id = it.key, createdAt = t, updatedAt = t, at = it.at, kind = it.kind, points = it.points)
            },
        )
        val data = loadData()
        val level = levelProgress(data.points.sumOf { it.points }).level
        if (level > levelBefore) {
            // R_LEVEL_NOLOSE: a level moment is written once per level and never removed.
            dao.addMomentIfNew(JourneyMomentEntity("level:$level", t, t, t, level.toString(), MomentKind.LEVEL))
        }

        val infos = catalog()
        val defs: List<BadgeDefinition> = infos.mapNotNull { it.definition }
        val awarded = badgesToAward(defs, data, clock, dao.earnedBadgeCodes().toSet())
        val byCode = infos.associateBy { it.code }
        awarded.mapNotNull { d ->
            // IGNORE keeps the first earnedAt if two refreshes ever race (a badge is earned once).
            if (dao.earnBadge(BadgeEntity(id = d.code, createdAt = t, updatedAt = t, earnedAt = t)) == INSERT_FAILED) {
                null
            } else {
                byCode[d.code]?.also {
                    dao.addMomentIfNew(JourneyMomentEntity("badge:${it.code}", t, t, t, it.title, MomentKind.BADGE))
                }
            }
        }
    }

    suspend fun summary(): ProgressSummary {
        val data = loadData()
        val clock = Clock(zone(), today())
        val earned = dao.badges().associate { it.id to it.earnedAt }
        val points = data.points.sumOf { it.points }
        return ProgressSummary(
            points = points,
            level = levelProgress(points),
            streak = computeStreak(activeDays(data, clock.zone), clock.today),
            finishedWorkouts = data.workouts.size,
            personalRecords = countPersonalRecords(data.sets),
            badges = catalog().map { BadgeState(it, earned[it.code]) },
            moments = dao.moments(MOMENT_LIMIT),
        )
    }

    suspend fun addNote(id: String, text: String) {
        val t = now()
        dao.upsertMoment(JourneyMomentEntity(id, t, t, t, text.trim(), MomentKind.NOTE))
    }

    /** A measure of any type. Only the number the user typed is stored; no rule ever reads it for points. */
    suspend fun addMeasure(id: String, type: String, value: Double, unit: String) {
        val t = now()
        dao.upsertMeasure(MeasureEntity(id, t, t, type, value, unit, t))
    }

    suspend fun measures(type: String): List<MeasureEntity> = dao.measures(type)

    private companion object {
        const val INSERT_FAILED = -1L
        const val MOMENT_LIMIT = 30
    }
}

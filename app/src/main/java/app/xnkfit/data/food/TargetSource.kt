package app.xnkfit.data.food

import app.xnkfit.data.db.ProfileEntity
import app.xnkfit.data.db.XnkDatabase
import app.xnkfit.domain.food.DayTargets
import app.xnkfit.domain.rules.ActivityLevel
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.rules.Goal
import app.xnkfit.domain.rules.Profile
import app.xnkfit.domain.rules.Screening
import app.xnkfit.domain.rules.Sex
import app.xnkfit.domain.rules.evaluateTargets
import app.xnkfit.domain.rules.sexFloorKcal
import kotlin.math.ceil
import kotlin.math.max

/** What the food screens need from the target (B-02 `evaluateTargets`). [numbersHidden] is R_GATE_ED. */
data class FoodTargets(val targets: DayTargets, val floorKcal: Int, val numbersHidden: Boolean)

fun interface TargetSource {
    /** Null when no target exists (gates not passed): the food screens then show no target at all. */
    suspend fun current(): FoodTargets?
}

/**
 * The stored profile has only the summary of the screening (risk yes/no, pregnant, eating history), not each answer,
 * and no activity level yet (B-05 decision: "jarang" stands in). Any risk answer is rebuilt as one risk answer, which
 * keeps every gate that onboarding applied. B-11 replaces this with the target card's own calculation.
 */
fun foodTargetsFrom(profile: ProfileEntity, weightKg: Double?, currentYear: Int): FoodTargets? {
    if (!profile.screeningDone) return null
    val sex = when (profile.sex) {
        "laki_laki" -> Sex.LAKI_LAKI
        "perempuan" -> Sex.PEREMPUAN
        else -> null
    }
    val goal = when (profile.goal) {
        "turun" -> Goal.TURUN
        "otot" -> Goal.NAIK
        else -> Goal.JAGA
    }
    val risk = profile.healthRisk
    val screening = Screening(
        heartOrBloodPressure = risk, chestPainOnActivity = false, ongoingInjury = false, otherTreatedCondition = false,
        doctorRestriction = false,
        pregnantOrBreastfeeding = if (sex == Sex.PEREMPUAN) profile.pregnant else null,
        eatingDisorderHistory = profile.eatingHistory == "ya",
    )
    val r = evaluateTargets(
        Profile(sex, profile.birthYear, profile.heightCm, weightKg, ActivityLevel.JARANG, goal),
        screening, currentYear,
    ) as? GateResult.Ready ?: return null
    val floor = max(sexFloorKcal(sex ?: return null), ceil(r.bmrKcal).toInt())
    return FoodTargets(
        DayTargets(r.targetKcal, r.macros.proteinG, r.macros.carbG, r.macros.fatG),
        floorKcal = floor,
        numbersHidden = r.numbersHidden,
    )
}

class RoomTargetSource(private val db: XnkDatabase, private val currentYear: () -> Int) : TargetSource {
    override suspend fun current(): FoodTargets? {
        val profile = db.profileDao().get() ?: return null
        val weight = db.profileDao().latestMeasure("weight")?.value
        return foodTargetsFrom(profile, weight, currentYear())
    }
}

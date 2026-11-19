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
import app.xnkfit.domain.today.withPaceRaise
import kotlin.math.ceil
import kotlin.math.max

/** What the food screens need from the target (B-02 `evaluateTargets`). [numbersHidden] is R_GATE_ED. */
data class FoodTargets(val targets: DayTargets, val floorKcal: Int, val numbersHidden: Boolean)

fun interface TargetSource {
    /** Null when no target exists (gates not passed): the food screens then show no target at all. */
    suspend fun current(): FoodTargets?
}

/** Profile goal code to the rules' goal. "otot" is "naik". An unknown or empty code maintains. */
fun goalFromCode(code: String?): Goal = when (code) {
    "turun" -> Goal.TURUN // text-ok: stored code
    "otot" -> Goal.NAIK // text-ok: stored code
    else -> Goal.JAGA
}

/**
 * The stored profile has only the summary of the screening (risk yes/no, pregnant, eating history), not each answer,
 * and no activity level yet (B-05 decision: "jarang" stands in). Any risk answer is rebuilt as one risk answer, which
 * keeps every gate that onboarding applied. The result is the one the Hari ini target card and the food screens share
 * (B-11); [goalCode] and [weightKg] are the ones the target was last calculated with, not necessarily today's.
 */
fun readyFrom(profile: ProfileEntity, weightKg: Double?, goalCode: String?, currentYear: Int): GateResult? {
    if (!profile.screeningDone) return GateResult.ScreeningNeeded
    val sex = when (profile.sex) {
        "laki_laki" -> Sex.LAKI_LAKI // text-ok: stored code
        "perempuan" -> Sex.PEREMPUAN // text-ok: stored code
        else -> null
    }
    val risk = profile.healthRisk
    val screening = Screening(
        heartOrBloodPressure = risk, chestPainOnActivity = false, ongoingInjury = false, otherTreatedCondition = false,
        doctorRestriction = false,
        pregnantOrBreastfeeding = if (sex == Sex.PEREMPUAN) profile.pregnant else null,
        eatingDisorderHistory = profile.eatingHistory == "ya", // text-ok: stored code
    )
    return evaluateTargets(
        Profile(sex, profile.birthYear, profile.heightCm, weightKg, ActivityLevel.JARANG, goalFromCode(goalCode ?: profile.goal)),
        screening, currentYear,
    )
}

fun foodTargetsFrom(profile: ProfileEntity, weightKg: Double?, currentYear: Int, goalCode: String? = null, raiseKcal: Int = 0): FoodTargets? {
    val ready = readyFrom(profile, weightKg, goalCode, currentYear) as? GateResult.Ready ?: return null
    return foodTargetsOf(ready, weightKg ?: return null, raiseKcal, profile.sex)
}

fun foodTargetsOf(ready: GateResult.Ready, weightKg: Double, raiseKcal: Int, sexCode: String?): FoodTargets? {
    val sex = if (sexCode == "laki_laki") Sex.LAKI_LAKI else if (sexCode == "perempuan") Sex.PEREMPUAN else return null // text-ok: stored codes
    val r = withPaceRaise(ready, weightKg, raiseKcal)
    val floor = max(sexFloorKcal(sex), ceil(r.bmrKcal).toInt())
    return FoodTargets(
        DayTargets(r.targetKcal, r.macros.proteinG, r.macros.carbG, r.macros.fatG),
        floorKcal = floor,
        numbersHidden = r.numbersHidden,
    )
}

/** Reads the weight and goal the target was last calculated with (B-11 `TargetBasisStore`), so Makan and Hari ini agree. */
class RoomTargetSource(
    private val db: XnkDatabase,
    private val basis: TargetBasisStore? = null,
    private val currentYear: () -> Int,
) : TargetSource {
    override suspend fun current(): FoodTargets? {
        val profile = db.profileDao().get() ?: return null
        val b = basis?.load()
        val weight = b?.weightKg ?: db.profileDao().latestMeasure("weight")?.value // text-ok: stored code
        return foodTargetsFrom(profile, weight, currentYear(), b?.goal, b?.raiseKcal ?: 0)
    }
}

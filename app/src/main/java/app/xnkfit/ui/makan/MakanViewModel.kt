package app.xnkfit.ui.makan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.xnkfit.data.food.FoodPrefs
import app.xnkfit.data.food.FoodPrefsStore
import app.xnkfit.data.food.FoodRepository
import app.xnkfit.data.food.FoodTargets
import app.xnkfit.data.food.ShoppingTickStore
import app.xnkfit.data.food.TargetSource
import app.xnkfit.domain.food.Allergen
import app.xnkfit.domain.food.Budget
import app.xnkfit.domain.food.BuyFrequency
import app.xnkfit.domain.food.DaySummary
import app.xnkfit.domain.food.FoodItem
import app.xnkfit.domain.food.HouseholdMeasure
import app.xnkfit.domain.food.LogRow
import app.xnkfit.domain.food.MealPackage
import app.xnkfit.domain.food.MealSlot
import app.xnkfit.domain.food.PlannedMeal
import app.xnkfit.domain.food.PlannerInput
import app.xnkfit.domain.food.PortionChoice
import app.xnkfit.domain.food.ShopLine
import app.xnkfit.domain.food.daySummary
import app.xnkfit.domain.food.defaultPortion
import app.xnkfit.domain.food.gramsOf
import app.xnkfit.domain.food.plannedDayKcal
import app.xnkfit.domain.food.plannedDayProtein
import app.xnkfit.domain.food.planWeek
import app.xnkfit.domain.food.santanCount
import app.xnkfit.domain.food.shoppingList
import app.xnkfit.domain.food.slotOptions
import app.xnkfit.domain.food.stepCount
import app.xnkfit.domain.food.withChoice
import app.xnkfit.domain.food.withCookOnce
import app.xnkfit.domain.food.withEatOut
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

enum class MakanRoute { HOME, PICK, PLANNER, SHOPPING }

enum class PickTab { RECENT, FAVOURITE, OWN }

/** One row of the day log as the screen shows it. [kcal] is null in no-numbers mode (R_GATE_ED). */
data class LogLine(
    val id: String,
    val slot: MealSlot,
    val title: String?,
    val kcal: Int?,
    val grams: Int?,
    val estimate: Boolean,
    val quick: Boolean,
)

/** The food being added: its portion, in household measures first (M-6). */
data class PortionState(
    val food: FoodItem,
    val choice: PortionChoice,
    val count: Double,
    val gramsText: String,
) {
    val grams: Double
        get() = when (choice) {
            is PortionChoice.Household -> gramsOf(choice.measure, count)
            PortionChoice.Grams -> gramsText.replace(',', '.').toDoubleOrNull() ?: 0.0
        }
}

data class PickState(
    val slot: MealSlot = MealSlot.PAGI,
    val query: String = "",
    val tab: PickTab = PickTab.RECENT,
    val results: List<FoodItem> = emptyList(),
    val list: List<FoodItem> = emptyList(),
    val portion: PortionState? = null,
    val quickOpen: Boolean = false,
    val ownOpen: Boolean = false,
    val error: Boolean = false,
)

data class MakanState(
    val route: MakanRoute = MakanRoute.HOME,
    val loaded: Boolean = false,
    val day: String = "",
    val weekStart: String = "",
    val todayIndex: Int = 1,
    val summary: DaySummary? = null,
    val hasTarget: Boolean = false,
    val numbersHidden: Boolean = false,
    val lines: List<LogLine> = emptyList(),
    val prefs: FoodPrefs = FoodPrefs(),
    val prefsSaved: Boolean = false,
    val pick: PickState = PickState(),
    val plan: List<PlannedMeal> = emptyList(),
    val packages: Map<String, MealPackage> = emptyMap(),
    val options: Map<Pair<Int, MealSlot>, List<MealPackage>> = emptyMap(),
    val dayKcal: Map<Int, Int> = emptyMap(),
    /** R_PLAN_PROT: protein g per planned day and the R_PROT range; empty / null when numbers are not shown. */
    val dayProtein: Map<Int, Int> = emptyMap(),
    val proteinRange: IntRange? = null,
    val santan: Int = 0,
    val daysBelowFloor: Set<Int> = emptySet(),
    val shopping: List<ShopLine> = emptyList(),
    val ticks: Set<String> = emptySet(),
    val failed: Boolean = false,
)

/**
 * Makan: day log, picking foods, week planner, shopping list (PRD M-1…M-10). All rules are in `domain.food`;
 * this class only reads, writes and holds what the screens show. Every write ends with [refresh], so the screens never
 * hold numbers the database does not have.
 */
class MakanViewModel(
    private val repo: FoodRepository,
    private val targets: TargetSource,
    private val prefsStore: FoodPrefsStore,
    private val ticksStore: ShoppingTickStore,
    private val today: () -> LocalDate,
    private val activityKcalToday: suspend () -> Int? = { null },
) : ViewModel() {
    private val _state = MutableStateFlow(MakanState())
    val state: StateFlow<MakanState> = _state.asStateFlow()

    private var cachedTargets: FoodTargets? = null
    private var searchJob: Job? = null
    private var packagesCache: List<MealPackage> = emptyList()

    init { launch { load() } }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(failed = true) } }
        }
    }

    private suspend fun load() {
        val d = today()
        val monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        packagesCache = repo.packages()
        _state.update {
            it.copy(
                day = d.toString(), weekStart = monday.toString(), todayIndex = d.dayOfWeek.value,
                packages = packagesCache.associateBy { p -> p.id }, prefs = prefsStore.load(),
            )
        }
        refresh()
    }

    // ---- reading ----

    private fun plannerInput(prefs: FoodPrefs, t: FoodTargets?, fixed: List<PlannedMeal>): PlannerInput = PlannerInput(
        packages = packagesCache, avoid = prefs.avoid, budget = prefs.budget, buys = prefs.buys,
        targetKcal = t?.targets?.kcal ?: DEFAULT_TARGET_KCAL, floorKcal = t?.floorKcal ?: DEFAULT_FLOOR_KCAL,
        fixed = fixed, seed = _state.value.weekStart.hashCode(), proteinRangeG = t?.proteinRangeG,
    )

    /** Rebuilds everything the screens show from the database. */
    suspend fun refresh() {
        val s = _state.value
        val t = targets.current().also { cachedTargets = it }
        val hidden = t?.numbersHidden == true
        val rows = repo.logOfDay(s.day)
        val lines = rows.map { r -> toLine(r, hidden) }
        val plan = repo.loadPlan(s.weekStart)
        val inp = plannerInput(s.prefs, t, emptyList())
        val byId = s.packages
        val options = HashMap<Pair<Int, MealSlot>, List<MealPackage>>()
        if (plan.isNotEmpty()) {
            for (d in 1..DAYS) for (slot in MealSlot.entries) options[d to slot] = slotOptions(inp, plan, d, slot)
        }
        val dayKcal = (1..DAYS).associateWith { plannedDayKcal(plan, byId, it).roundToInt() }
        val showProtein = !hidden && t?.proteinRangeG != null
        val dayProtein = if (showProtein) (1..DAYS).associateWith { plannedDayProtein(plan, byId, it).roundToInt() } else emptyMap()
        val ticks = ticksStore.observeOnce(s.weekStart)
        val shopping = shoppingList(plan, byId, s.todayIndex) { id -> measuresCache[id].orEmpty() }
            .also { loadMeasures(it) }
        _state.update {
            it.copy(
                loaded = true, failed = false, hasTarget = t != null, numbersHidden = hidden,
                summary = daySummary(rows, t?.targets, hidden, activityKcalToday()),
                lines = lines, plan = plan, options = options, dayKcal = dayKcal,
                dayProtein = dayProtein, proteinRange = if (showProtein) t?.proteinRangeG else null,
                santan = santanCount(plan, byId), shopping = shopping, ticks = ticks,
            )
        }
    }

    private val measuresCache = HashMap<String, List<HouseholdMeasure>>()
    private val nameCache = HashMap<String, FoodItem?>()

    private suspend fun loadMeasures(lines: List<ShopLine>) {
        var added = false
        lines.forEach { l ->
            if (l.foodId !in measuresCache) {
                measuresCache[l.foodId] = repo.food(l.foodId)?.household.orEmpty()
                added = true
            }
        }
        if (added) {
            val s = _state.value
            _state.update { it.copy(shopping = shoppingList(s.plan, s.packages, s.todayIndex) { id -> measuresCache[id].orEmpty() }) }
        }
    }

    private suspend fun toLine(r: LogRow, hidden: Boolean): LogLine {
        val entry = repo.logEntry(r.id)
        val food = entry?.foodId?.let { id -> nameCache.getOrPut(id) { repo.food(id) } }
        return LogLine(
            id = r.id, slot = r.slot, title = food?.nameId,
            kcal = if (hidden) null else r.nutrients.kcal.roundToInt(),
            grams = entry?.grams?.roundToInt(), estimate = food?.isEstimate == true, quick = r.quick,
        )
    }

    // ---- navigation ----

    fun open(route: MakanRoute) {
        _state.update { it.copy(route = route) }
        if (route == MakanRoute.PICK) launch { loadPickList() }
        if (route == MakanRoute.SHOPPING || route == MakanRoute.PLANNER) launch { refresh() }
    }

    /** Back inside Makan: returns true when it handled the press. */
    fun back(): Boolean {
        val s = _state.value
        val p = s.pick
        return when {
            s.route == MakanRoute.HOME -> false
            s.route == MakanRoute.PICK && p.portion != null -> { _state.update { it.copy(pick = p.copy(portion = null)) }; true }
            s.route == MakanRoute.PICK && (p.quickOpen || p.ownOpen) -> { _state.update { it.copy(pick = p.copy(quickOpen = false, ownOpen = false)) }; true }
            else -> { _state.update { it.copy(route = MakanRoute.HOME) }; true }
        }
    }

    // ---- preferences (M-9) ----

    fun toggleAllergen(a: Allergen) = _state.update {
        val next = if (a in it.prefs.avoid) it.prefs.avoid - a else it.prefs.avoid + a
        it.copy(prefs = it.prefs.copy(avoid = next), prefsSaved = false)
    }

    fun setBudget(b: Budget) = _state.update { it.copy(prefs = it.prefs.copy(budget = b), prefsSaved = false) }

    fun setBuys(b: BuyFrequency) = _state.update { it.copy(prefs = it.prefs.copy(buys = b), prefsSaved = false) }

    fun savePrefs() = launch {
        prefsStore.save(_state.value.prefs)
        _state.update { it.copy(prefsSaved = true) }
    }

    // ---- picking and logging (M-6, M-7) ----

    fun startPick(slot: MealSlot) {
        _state.update { it.copy(pick = PickState(slot = slot), route = MakanRoute.PICK) }
        launch { loadPickList() }
    }

    fun selectSlot(slot: MealSlot) = _state.update { it.copy(pick = it.pick.copy(slot = slot)) }

    fun selectTab(tab: PickTab) {
        _state.update { it.copy(pick = it.pick.copy(tab = tab)) }
        launch { loadPickList() }
    }

    private suspend fun loadPickList() {
        val tab = _state.value.pick.tab
        val list = when (tab) {
            PickTab.RECENT -> repo.recent()
            PickTab.FAVOURITE -> repo.favourites()
            PickTab.OWN -> repo.ownFoods()
        }
        _state.update { it.copy(pick = it.pick.copy(list = list)) }
    }

    /** Called on every keystroke. The previous search is cancelled; a result for old text never replaces newer text. */
    fun setQuery(text: String) {
        _state.update { it.copy(pick = it.pick.copy(query = text)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val results = repo.search(text)
                _state.update { if (it.pick.query == text) it.copy(pick = it.pick.copy(results = results)) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(pick = it.pick.copy(error = true)) }
            }
        }
    }

    fun chooseFood(food: FoodItem) {
        val (choice, count) = defaultPortion(food)
        _state.update { it.copy(pick = it.pick.copy(portion = PortionState(food, choice, count, DEFAULT_GRAMS_TEXT))) }
    }

    fun choosePortion(choice: PortionChoice) = _state.update {
        val p = it.pick.portion ?: return@update it
        val count = (choice as? PortionChoice.Household)?.measure?.qty ?: p.count
        it.copy(pick = it.pick.copy(portion = p.copy(choice = choice, count = count)))
    }

    fun stepPortion(up: Boolean) = _state.update {
        val p = it.pick.portion ?: return@update it
        it.copy(pick = it.pick.copy(portion = p.copy(count = stepCount(p.count, up))))
    }

    fun setGramsText(text: String) = _state.update {
        val p = it.pick.portion ?: return@update it
        it.copy(pick = it.pick.copy(portion = p.copy(gramsText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(GRAMS_TEXT_MAX))))
    }

    fun logPortion() = launch {
        val s = _state.value
        val p = s.pick.portion ?: return@launch
        if (p.grams <= 0.0) return@launch
        repo.logFood(s.day, s.pick.slot, p.food, p.grams)
        _state.update { it.copy(pick = it.pick.copy(portion = null), route = MakanRoute.HOME) }
        refresh()
    }

    fun openQuick(open: Boolean) = _state.update { it.copy(pick = it.pick.copy(quickOpen = open), route = MakanRoute.PICK) }

    fun logQuick(kcalText: String) = launch {
        val s = _state.value
        val kcal = kcalText.replace(',', '.').toDoubleOrNull()
        if (kcal == null || kcal <= 0.0) {
            _state.update { it.copy(pick = it.pick.copy(error = true)) }
            return@launch
        }
        repo.logQuick(s.day, s.pick.slot, kcal)
        _state.update { it.copy(pick = it.pick.copy(quickOpen = false, error = false), route = MakanRoute.HOME) }
        refresh()
    }

    fun openOwn(open: Boolean) = _state.update { it.copy(pick = it.pick.copy(ownOpen = open, error = false)) }

    /** "Buat makanan sendiri": per 100 g values and an optional serving weight. */
    fun saveOwn(name: String, kcal: String, protein: String, carb: String, fat: String, servingG: String) = launch {
        fun n(t: String) = t.replace(',', '.').toDoubleOrNull()
        val k = n(kcal)
        if (name.isBlank() || k == null || k < 0.0) {
            _state.update { it.copy(pick = it.pick.copy(error = true)) }
            return@launch
        }
        val id = repo.saveOwnFood(name, k, n(protein) ?: 0.0, n(carb) ?: 0.0, n(fat) ?: 0.0, n(servingG))
        val food = repo.food(id)
        _state.update { it.copy(pick = it.pick.copy(ownOpen = false, error = false, tab = PickTab.OWN)) }
        loadPickList()
        if (food != null) chooseFood(food)
    }

    fun deleteLog(id: String) = launch { repo.deleteLog(id); refresh() }

    fun copyYesterday() = launch {
        val s = _state.value
        repo.copyLog(LocalDate.parse(s.day).minusDays(1).toString(), s.day)
        refresh()
    }

    // ---- week plan (M-3, M-4) ----

    private fun input(): PlannerInput = plannerInput(_state.value.prefs, cachedTargets, _state.value.plan.filter { it.locked })

    /** "Isi ulang otomatis": keeps locked cells, fills the rest by the planner rules. */
    fun autoFill() = launch {
        val s = _state.value
        val result = planWeek(input())
        repo.savePlan(s.weekStart, result.meals)
        refresh()
        _state.update { it.copy(daysBelowFloor = result.daysBelowFloor) }
    }

    private fun savePlanChange(next: List<PlannedMeal>) = launch {
        repo.savePlan(_state.value.weekStart, next)
        refresh()
    }

    /** M-4: a manual choice is never blocked. */
    fun choose(day: Int, slot: MealSlot, packageId: String) = savePlanChange(withChoice(_state.value.plan, day, slot, packageId, locked = false))

    fun toggleLock(day: Int, slot: MealSlot) = savePlanChange(
        _state.value.plan.map { if (it.day == day && it.slot == slot) it.copy(locked = !it.locked) else it },
    )

    fun cookOnce(day: Int, slot: MealSlot) = savePlanChange(withCookOnce(_state.value.plan, day, slot))

    fun eatOut(day: Int, slot: MealSlot) {
        val next = withEatOut(input(), _state.value.plan, day, slot) ?: return
        savePlanChange(next)
    }

    // ---- shopping (M-8) ----

    fun tick(foodId: String, on: Boolean) = launch {
        ticksStore.set(_state.value.weekStart, foodId, on)
        _state.update { it.copy(ticks = if (on) it.ticks + foodId else it.ticks - foodId) }
    }

    private companion object {
        const val DAYS = 7
        const val DEFAULT_TARGET_KCAL = 1750 // only used when no target exists; the floor below still holds
        const val DEFAULT_FLOOR_KCAL = 1500
        const val DEFAULT_GRAMS_TEXT = "100"
        const val GRAMS_TEXT_MAX = 6
    }
}

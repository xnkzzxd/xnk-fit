package app.xnkfit.domain.food

/** Small hand-made packages so planner tests can be checked by reading them. */
internal fun pkg(
    id: String,
    slot: MealSlot,
    kcal: Double,
    budget: Budget = Budget.HEMAT,
    mode: PackageMode = PackageMode.MASAK,
    santan: Boolean = false,
    goreng: Boolean = false,
    main: String? = null,
    allergens: Set<Allergen> = emptySet(),
    halal: Boolean = true,
    profile: String = "jawa",
    items: List<PackageItem> = emptyList(),
) = MealPackage(
    id = id, profile = profile, slot = slot, nameId = id, mode = mode, nutrients = Nutrients(kcal, 10.0, 20.0, 5.0),
    budget = budget, halal = halal, allergens = allergens, isEstimate = false, santan = santan, goreng = goreng,
    mainProtein = main, items = items,
)

/** Four slots with [perSlot] packages each; kcal 400/600/500/150 plus a small step so no two tie. */
internal fun basicPool(perSlot: Int = 4): List<MealPackage> =
    MealSlot.entries.flatMap { slot ->
        val base = when (slot) {
            MealSlot.PAGI -> 400.0
            MealSlot.SIANG -> 600.0
            MealSlot.MALAM -> 500.0
            MealSlot.CAMILAN -> 150.0
        }
        (1..perSlot).map { i -> pkg("${slot.name}_$i", slot, base + i * 5.0) }
    }

internal fun input(
    packages: List<MealPackage>,
    target: Int = 1700,
    floor: Int = 1500,
    budget: Budget = Budget.HEMAT,
    buys: BuyFrequency = BuyFrequency.SOMETIMES,
    avoid: Set<Allergen> = emptySet(),
    fixed: List<PlannedMeal> = emptyList(),
    profiles: Set<String>? = null,
    seed: Int = 0,
) = PlannerInput(packages, avoid, budget, buys, target, floor, profiles, fixed, seed)

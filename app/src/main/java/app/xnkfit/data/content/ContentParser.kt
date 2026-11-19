package app.xnkfit.data.content

import app.xnkfit.data.db.ContentDocEntity
import app.xnkfit.data.db.ExerciseEntity
import app.xnkfit.data.db.FoodEntity
import app.xnkfit.data.db.MealPackageEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One content file, parsed and checked, ready to be written. Nothing is written until every file parsed. */
class ParsedFile(
    val file: ContentFile,
    val exercises: List<ExerciseEntity> = emptyList(),
    val foods: List<FoodEntity> = emptyList(),
    val mealPackages: List<MealPackageEntity> = emptyList(),
    val docs: List<ContentDocEntity> = emptyList(),
    /** Ids of rows left out and why (packaged rows with no per-100 g basis, inactive exercises). */
    val skipped: List<String> = emptyList(),
)

/** Pure parsing of the shipped JSON into Room rows. Any malformed row throws [ContentException]: all or nothing. */
object ContentParser {
    const val DOC_BADGES = "badges"
    const val DOC_MEAL_META = "meal-packages-meta"

    fun parse(file: ContentFile, bytes: ByteArray, now: Long): ParsedFile {
        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: JSONException) {
            throw ContentException("${file.fileName}: not valid JSON", e)
        }
        val parsed = try {
            when (file) {
                ContentFile.EXERCISES -> exercises(root, now)
                ContentFile.FOODS_TKPI -> foods(file, root.arr("foods"), now)
                ContentFile.FOODS_ESTIMATES -> foods(file, root.arr("foods"), now)
                ContentFile.FOODS_REGIONAL -> foods(file, root.arr("rows"), now)
                ContentFile.FOODS_PACKAGED -> packaged(root.arr("foods"), now)
                ContentFile.BADGES -> badges(root, now)
                ContentFile.MEAL_PACKAGES -> mealPackages(root, now)
            }
        } catch (e: JSONException) {
            throw ContentException("${file.fileName}: ${e.message}", e)
        }
        val rows = parsed.exercises.size + parsed.foods.size + parsed.mealPackages.size + parsed.docs.size
        if (rows == 0 && !file.mayBeEmpty) throw ContentException("${file.fileName}: no rows")
        return parsed
    }

    private fun exercises(root: JSONObject, now: Long): ParsedFile {
        val list = root.arr("exercises")
        val rows = ArrayList<ExerciseEntity>()
        val skipped = ArrayList<String>()
        val seen = HashSet<String>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val id = o.getString("id")
            if (!seen.add(id)) throw ContentException("exercises.json: duplicate id $id")
            // active=false is how an exercise is retired (IDs stay stable forever); it is not offered any more.
            if (!o.optBoolean("active", true)) { skipped += "$id: inactive"; continue }
            val nameEn = o.getString("name_en")
            val cues = o.optJSONArray("cues_en")
            rows += ExerciseEntity(
                id = id, createdAt = now, updatedAt = now,
                // The file has English names only so far; the Indonesian name falls back to it until C-09 adds name_id.
                nameId = o.optStringOrNull("name_id") ?: nameEn,
                nameEn = nameEn,
                pattern = o.getString("pattern"),
                musclesJson = o.getJSONObject("muscles").toString(),
                level = o.get("level").toString(),
                variantOf = o.optStringOrNull("variantOf"),
                cuesEn = cues?.let { c -> (0 until c.length()).joinToString("\n") { c.getString(it) } },
                hintId = o.optStringOrNull("hint_id"),
            )
        }
        return ParsedFile(ContentFile.EXERCISES, exercises = rows, skipped = skipped)
    }

    private fun foods(file: ContentFile, list: JSONArray, now: Long): ParsedFile {
        val rows = ArrayList<FoodEntity>()
        val seen = HashSet<String>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val id = o.getString("id")
            checkPrefix(file, id)
            if (!seen.add(id)) throw ContentException("${file.fileName}: duplicate id $id")
            rows += FoodEntity(
                id = id, createdAt = now, updatedAt = now,
                source = o.getString("source"),
                nameId = o.getString("name_id"),
                nameEn = o.optStringOrNull("name_en"),
                kcalPer100 = o.num("kcalPer100", id), protein = o.num("protein", id),
                carb = o.num("carb", id), fat = o.num("fat", id),
                fibre = o.numOrNull("fibre"), sodium = o.numOrNull("sodium"),
                householdJson = o.optJSONArray("household")?.takeIf { it.length() > 0 }?.toString(),
                isEstimate = o.optBoolean("isEstimate", false),
                halal = o.boolOrNull("halal"),
                allergensJson = o.optJSONArray("allergens")?.toString(),
            )
        }
        return ParsedFile(file, foods = rows)
    }

    /** Packaged rows list values per serving. Per 100 g is read when given, else computed from grams; with neither, the row is skipped. */
    private fun packaged(list: JSONArray, now: Long): ParsedFile {
        val file = ContentFile.FOODS_PACKAGED
        val rows = ArrayList<FoodEntity>()
        val skipped = ArrayList<String>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val id = o.getString("id")
            checkPrefix(file, id)
            val per100 = o.optJSONObject("per100g")
            val serving = o.optJSONObject("perServing")
            val grams = o.optJSONObject("serving")?.numOrNull("grams")
            val m: JSONObject?
            val factor: Double
            when {
                per100 != null -> { m = per100; factor = 1.0 }
                serving != null && grams != null && grams > 0 -> { m = serving; factor = 100.0 / grams }
                else -> { skipped += "$id: no per-100 g basis (serving in ml, no density source)"; continue }
            }
            fun v(k: String): Double? = m.numOrNull(k)?.times(factor)
            rows += FoodEntity(
                id = id, createdAt = now, updatedAt = now,
                source = "packaged",
                nameId = o.getString("name_id"),
                nameEn = o.optStringOrNull("name_en"),
                kcalPer100 = v("kcal") ?: throw ContentException("foods-packaged.json: $id has no kcal"),
                protein = v("protein") ?: throw ContentException("foods-packaged.json: $id has no protein"),
                carb = v("carb") ?: throw ContentException("foods-packaged.json: $id has no carb"),
                fat = v("fat") ?: throw ContentException("foods-packaged.json: $id has no fat"),
                fibre = v("fibre"), sodium = v("sodium"),
                householdJson = o.optJSONArray("household")?.takeIf { it.length() > 0 }?.toString(),
                isEstimate = o.optBoolean("isEstimate", true),
                halal = o.boolOrNull("halal"),
                allergensJson = o.optJSONArray("allergens")?.toString(),
            )
        }
        return ParsedFile(file, foods = rows, skipped = skipped)
    }

    private fun badges(root: JSONObject, now: Long): ParsedFile {
        if (root.arr("badges").length() == 0) throw ContentException("badges.json: no badges")
        return ParsedFile(ContentFile.BADGES, docs = listOf(ContentDocEntity(DOC_BADGES, now, now, root.toString())))
    }

    private fun mealPackages(root: JSONObject, now: Long): ParsedFile {
        val list = root.arr("packages")
        val rows = ArrayList<MealPackageEntity>()
        val seen = HashSet<String>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val id = o.getString("id")
            if (!seen.add(id)) throw ContentException("meal-packages.json: duplicate id $id")
            rows += MealPackageEntity(id, now, now, o.getString("profile"), o.getString("slot"), o.toString())
        }
        // Everything except the packages (profiles, budgets, floor, own hook, tiers) travels as one document.
        val meta = JSONObject(root.toString()).also { it.remove("packages") }
        return ParsedFile(
            ContentFile.MEAL_PACKAGES, mealPackages = rows,
            docs = listOf(ContentDocEntity(DOC_MEAL_META, now, now, meta.toString())),
        )
    }

    private fun checkPrefix(file: ContentFile, id: String) {
        val p = file.idPrefix!!
        if (!id.startsWith(p)) throw ContentException("${file.fileName}: id $id does not start with $p")
    }

    private fun JSONObject.arr(key: String): JSONArray = getJSONArray(key)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key).ifEmpty { null }

    private fun JSONObject.numOrNull(key: String): Double? {
        if (isNull(key)) return null
        val v = getDouble(key)
        if (v.isNaN() || v.isInfinite() || v < 0) throw ContentException("$key is not a valid number")
        return v
    }

    private fun JSONObject.num(key: String, id: String): Double =
        numOrNull(key) ?: throw ContentException("$id: $key is missing")

    private fun JSONObject.boolOrNull(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)
}

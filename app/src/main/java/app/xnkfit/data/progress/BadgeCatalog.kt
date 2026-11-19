package app.xnkfit.data.progress

import app.xnkfit.domain.progress.BadgeDefinition
import app.xnkfit.domain.progress.BadgeMetric
import org.json.JSONObject

/** What the screen shows for one badge: text and icon come from `content/badges.json`, not from the code. */
data class BadgeInfo(
    val code: String,
    val order: Int,
    val title: String,
    val message: String,
    val icon: String,
    val conditionEn: String,
    /** Null when the file names a metric or operator this app does not know: shown, never earned. */
    val definition: BadgeDefinition?,
)

/** Reads the shipped `badges` document (Content.md §6). */
object BadgeCatalog {
    private const val OP_AT_LEAST = ">="

    fun parse(json: String): List<BadgeInfo> {
        val arr = JSONObject(json).getJSONArray("badges")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { b ->
            val code = b.getString("code")
            val order = b.getInt("order")
            val cond = b.getJSONObject("condition")
            val metric = BadgeMetric.fromKey(cond.optString("metric"))
            val def = if (metric != null && cond.optString("op") == OP_AT_LEAST) {
                BadgeDefinition(code, order, metric, cond.getInt("value"))
            } else {
                null
            }
            BadgeInfo(
                code = code,
                order = order,
                title = b.getString("title_id"),
                message = b.getString("message_id"),
                icon = b.optString("icon"),
                conditionEn = cond.optString("text_en"),
                definition = def,
            )
        }.sortedBy { it.order }
    }
}

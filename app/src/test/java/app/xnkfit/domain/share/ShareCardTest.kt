package app.xnkfit.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShareCardTest {
    private val day = LocalDate.of(2026, 11, 19)
    private val workout = WorkoutFacts(day, "Full Body B", 52, 2, LiftFact("Goblet Squat", 16.0, 12))
    private val facts = CardFacts(
        workout = workout,
        progress = ProgressFacts(waistDeltaCm = -2.0, weightDeltaKg = -1.5, trackedDays = 30),
        consistency = ConsistencyFacts(streakWeeks = 5, level = 4, workouts = 12),
        food = FoodFacts(daysLogged = 7),
    )

    private fun card(o: ShareOptions, saya: Boolean = false, photo: String? = null) = buildCard(facts, o, saya, photo)!!

    @Test
    fun R_SHARE_NOKG_defaultIsOn() {
        assertTrue(ShareOptions().hideKg)
        assertTrue(card(ShareOptions()).noKg)
    }

    @Test
    fun R_SHARE_NOKG_workoutLiftLosesItsLoad() {
        assertNull(card(ShareOptions(kind = CardKind.WORKOUT)).lift!!.weightKg)
        assertEquals(16.0, card(ShareOptions(kind = CardKind.WORKOUT, hideKg = false)).lift!!.weightKg)
    }

    @Test
    fun R_SHARE_NOKG_progressHasNoWeightFieldUnlessAllowed() {
        val hidden = card(ShareOptions(kind = CardKind.PROGRESS))
        assertFalse(hidden.fields.any { it.id == FieldId.WEIGHT_DELTA })
        val shown = card(ShareOptions(kind = CardKind.PROGRESS, hideKg = false))
        assertEquals(-1.5, shown.fields.single { it.id == FieldId.WEIGHT_DELTA }.value, 0.0)
    }

    @Test
    fun R_SHARE_NOKG_sayaHidingWeightWinsOverTheSwitch() {
        val c = card(ShareOptions(kind = CardKind.PROGRESS, hideKg = false), saya = true)
        assertTrue(c.noKg)
        assertFalse(c.fields.any { it.id == FieldId.WEIGHT_DELTA })
        assertTrue(effectiveHideKg(optionHideKg = false, sayaHidesWeight = true))
        assertFalse(effectiveHideKg(optionHideKg = false, sayaHidesWeight = false))
    }

    @Test
    fun R_SHARE_NOKG_noCardCarriesAnAbsoluteWeight() {
        // The facts have no absolute weight field at all; the only kg numbers possible are a load and a change.
        CardKind.entries.forEach { kind ->
            val c = card(ShareOptions(kind = kind, hideKg = true))
            assertNull(c.lift?.weightKg)
            assertFalse(c.fields.any { it.id == FieldId.WEIGHT_DELTA })
        }
    }

    @Test
    fun R_SHARE_BRAND_offByDefaultUntilDownloadPageExistsAndOnWhenAsked() {
        assertFalse(ShareOptions().brand)
        assertFalse(card(ShareOptions()).brand)
        assertTrue(card(ShareOptions(brand = true)).brand)
    }

    @Test
    fun R_SHARE_FACE_onByDefaultAndPhotoOnlyOnProgressCardWhenAsked() {
        assertTrue(ShareOptions().blurFaces)
        assertNull(card(ShareOptions(kind = CardKind.PROGRESS), photo = "/p.jpg").photoPath)
        assertEquals("/p.jpg", card(ShareOptions(kind = CardKind.PROGRESS, withPhoto = true), photo = "/p.jpg").photoPath)
        assertNull(card(ShareOptions(kind = CardKind.WORKOUT, withPhoto = true), photo = "/p.jpg").photoPath)
        assertTrue(card(ShareOptions(kind = CardKind.PROGRESS, withPhoto = true), photo = "/p.jpg").blurFaces)
    }

    @Test
    fun R_SHARE_FACE_boxesGrowAndStayInsideThePicture() {
        val boxes = mosaicBoxes(listOf(Box(100, 100, 200, 200), Box(0, 0, 50, 50)), 220, 220)
        assertEquals(Box(60, 60, 220, 220), boxes[0])
        assertEquals(0, boxes[1].left)
        assertEquals(0, boxes[1].top)
        assertTrue(boxes.all { it.right <= 220 && it.bottom <= 220 })
    }

    @Test
    fun R_SHARE_PX_sizes() {
        assertEquals(1080 to 1920, CardSize.STORY.widthPx to CardSize.STORY.heightPx)
        assertEquals(1080 to 1080, CardSize.SQUARE.widthPx to CardSize.SQUARE.heightPx)
    }

    @Test
    fun R_SHARE_DATA_workoutFieldsInOrderAndZeroRecordsNotPrinted() {
        val c = card(ShareOptions(kind = CardKind.WORKOUT))
        assertEquals(listOf(FieldId.MINUTES, FieldId.RECORDS), c.fields.map { it.id })
        assertEquals(Headline.WORKOUT_TITLE, c.headline)
        val none = buildCard(CardFacts(workout = workout.copy(records = 0, title = null)), ShareOptions(), false, null)!!
        assertEquals(listOf(FieldId.MINUTES), none.fields.map { it.id })
        assertEquals(Headline.WORKOUT_UNTITLED, none.headline)
    }

    @Test
    fun R_SHARE_DATA_progressHeadlineIsWaistThenDays() {
        val c = card(ShareOptions(kind = CardKind.PROGRESS))
        assertEquals(Headline.WAIST_DELTA, c.headline)
        assertEquals(-2.0, c.headlineValue!!, 0.0)
        assertEquals(listOf(FieldId.DAYS), c.fields.map { it.id })
        val days = buildCard(CardFacts(progress = ProgressFacts(null, -1.0, 12)), ShareOptions(kind = CardKind.PROGRESS), false, null)!!
        assertEquals(Headline.TRACKED_DAYS, days.headline)
        assertTrue(days.fields.isEmpty())
    }

    @Test
    fun R_SHARE_DATA_consistencyAndFood() {
        val c = card(ShareOptions(kind = CardKind.CONSISTENCY))
        assertEquals(Headline.STREAK_WEEKS, c.headline)
        assertEquals(listOf(FieldId.LEVEL, FieldId.WORKOUTS), c.fields.map { it.id })
        val noStreak = buildCard(CardFacts(consistency = ConsistencyFacts(0, 2, 0)), ShareOptions(kind = CardKind.CONSISTENCY), false, null)!!
        assertEquals(Headline.LEVEL, noStreak.headline)
        val f = card(ShareOptions(kind = CardKind.FOOD))
        assertEquals(7.0, f.headlineValue!!, 0.0)
        assertEquals(R_SHARE_DATA_FOOD_WINDOW_DAYS.toDouble(), f.fields.single().value, 0.0)
    }

    @Test
    fun R_SHARE_DATA_cardWithoutDataIsNotOffered() {
        assertNull(buildCard(CardFacts(), ShareOptions(kind = CardKind.WORKOUT), false, null))
        assertNull(buildCard(CardFacts(food = FoodFacts(0)), ShareOptions(kind = CardKind.FOOD), false, null))
        assertNull(buildCard(CardFacts(progress = ProgressFacts(null, null, null)), ShareOptions(kind = CardKind.PROGRESS), false, null))
        assertEquals(emptyList<CardKind>(), availableKinds(CardFacts()))
        assertEquals(CardKind.entries, availableKinds(facts))
        assertEquals(listOf(CardKind.CONSISTENCY), availableKinds(CardFacts(consistency = ConsistencyFacts(1, 1, 0))))
    }

    @Test
    fun R_SHARE_DATA_styleSizeAndTemplateArePassedThrough() {
        val c = card(ShareOptions(style = CardStyle.ACCENT, size = CardSize.SQUARE, template = CardTemplate.BIG))
        assertEquals(CardStyle.ACCENT, c.style)
        assertEquals(CardSize.SQUARE, c.size)
        assertEquals(CardTemplate.BIG, c.template)
    }
}

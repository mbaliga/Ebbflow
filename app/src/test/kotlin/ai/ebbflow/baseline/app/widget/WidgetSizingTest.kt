package ai.ebbflow.baseline.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSizingTest {

    // What Android 12+ launchers report for a phone's cell grid (developer.android.com,
    // "widget sizing"): portrait (73n - 16) x (118m - 16) dp, landscape (142n - 15) x (66m - 15).
    private val portrait2x1 = 130 to 102
    private val portrait2x2 = 130 to 220
    private val portrait4x1 = 276 to 102
    private val portrait4x2 = 276 to 220
    private val portrait4x3 = 276 to 338
    private val landscape2x1 = 269 to 51
    private val landscape4x2 = 553 to 117
    private val landscape4x3 = 553 to 184

    private fun choose(template: WidgetTemplate, size: Pair<Int, Int>) =
        WidgetSizing.choose(template, size.first, size.second)

    @Test
    fun theDefault4x2GetsTheMediumLayoutAnd4x3GetsLarge() {
        for (template in WidgetTemplate.entries) {
            assertEquals(template.name, WidgetSize.MEDIUM, choose(template, portrait4x2))
            assertEquals(template.name, WidgetSize.LARGE, choose(template, portrait4x3))
            assertEquals(template.name, WidgetSize.LARGE, WidgetSizing.choose(template, 400, 456))
            // Landscape rows are short, so the large layouts would be cut there.
            assertEquals(template.name, WidgetSize.MEDIUM, choose(template, landscape4x3))
        }
    }

    @Test
    fun textWidgetsGoFromOneLineToSmallMediumAndLarge() {
        for (template in listOf(WidgetTemplate.STAT, WidgetTemplate.CARD)) {
            assertEquals(WidgetSize.COLLAPSED, choose(template, landscape2x1))
            assertEquals(WidgetSize.COLLAPSED, WidgetSizing.choose(template, 110, 40))
            assertEquals(WidgetSize.SMALL, choose(template, portrait2x1))
            assertEquals(WidgetSize.SMALL, choose(template, portrait2x2))
            assertEquals(WidgetSize.SMALL, choose(template, portrait4x1))
            assertEquals(WidgetSize.SMALL, WidgetSizing.choose(template, 250, 110))
            // A landscape 4x2 is too short for title, value and stats at 1.3x text.
            assertEquals(WidgetSize.SMALL, choose(template, landscape4x2))
            // Narrower than two cells still draws the one-line summary.
            assertEquals(WidgetSize.COLLAPSED, WidgetSizing.choose(template, 60, 300))
        }
    }

    @Test
    fun theImageWidgetKeepsItsLineAtEverySmallSize() {
        assertEquals(WidgetSize.SMALL, choose(WidgetTemplate.IMAGE, landscape2x1))
        assertEquals(WidgetSize.SMALL, choose(WidgetTemplate.IMAGE, portrait2x1))
        assertEquals(WidgetSize.SMALL, WidgetSizing.choose(WidgetTemplate.IMAGE, 60, 30))
        assertEquals(WidgetSize.SMALL, choose(WidgetTemplate.IMAGE, landscape4x2))
    }

    @Test
    fun padsCollapseToOneTapTargetBelowTwoByTwo() {
        assertEquals(WidgetSize.COLLAPSED, choose(WidgetTemplate.PADS, portrait2x1))
        assertEquals(WidgetSize.COLLAPSED, choose(WidgetTemplate.PADS, portrait4x1))
        assertEquals(WidgetSize.COLLAPSED, choose(WidgetTemplate.PADS, landscape2x1))
        assertEquals(WidgetSize.COLLAPSED, WidgetSizing.choose(WidgetTemplate.PADS, 300, 80))
        assertEquals(WidgetSize.SMALL, choose(WidgetTemplate.PADS, portrait2x2))
        assertEquals(WidgetSize.SMALL, WidgetSizing.choose(WidgetTemplate.PADS, 110, 110))
        // Too short for a title, a status line and 48 dp pads: one pad instead of two clipped ones.
        assertEquals(WidgetSize.SMALL, choose(WidgetTemplate.PADS, landscape4x2))
    }

    @Test
    fun everyTemplateOffersAtLeastThreeLayoutsToTheLauncher() {
        for (template in WidgetTemplate.entries) {
            val points = WidgetSizing.breakpoints(template)
            assertTrue(points.map { it.third }.toSet().size >= 3)
            // Each breakpoint maps back to its own layout through choose().
            points.forEach { (w, h, size) -> assertEquals(size, WidgetSizing.choose(template, w, h)) }
            // Never smaller than the previous one in either direction, so the launcher's
            // closest fitting size is also the largest fitting one.
            points.zipWithNext().forEach { (a, b) -> assertTrue(b.first >= a.first && b.second >= a.second) }
        }
    }

    @Test
    fun requestCodesAreUniquePerWidgetAndAction() {
        val slots = listOf(WidgetRequestCodes.BODY) + Pad.entries.map(WidgetRequestCodes::forPad)
        assertEquals(slots.size, slots.toSet().size)
        val codes = (1..50).flatMap { id -> slots.map { WidgetRequestCodes.of(id, it) } }
        assertEquals(codes.size, codes.toSet().size)
    }
}

package ai.ebbflow.baseline.app.widget

import ai.ebbflow.baseline.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the widget resources as files (the JVM test runs from the module directory): the
 * wording law, the colour rule, and how the widgets are registered.
 */
class WidgetResourcesTest {
    private val res = File("src/main/res")
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file).documentElement

    private fun children(parent: Element, tag: String): List<Element> {
        val nodes = parent.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun widgetStrings(): Map<String, String> =
        children(parse(File(res, "values/strings.xml")), "string")
            .filter { it.getAttribute("name").startsWith("widget_") }
            .associate { it.getAttribute("name") to it.textContent }

    @Test
    fun everyKeyNamesItsOwnWidgetString() {
        val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        for (key in WStr.entries) {
            assertEquals(key.name, "widget_${key.name.lowercase()}", names[key.res])
        }
    }

    @Test
    fun widgetCopyNeverDescribesThePerson() {
        // README: Ebbflow's values are not cognitive or clinical state estimates.
        val forbidden = Regex(
            "\\b(focus\\w*|attention|attentive|concentrat\\w*|mood|emotion\\w*|calm\\w*|stress\\w*|relax\\w*|mental\\w*|mind\\w*|state)\\b",
            RegexOption.IGNORE_CASE,
        )
        val strings = widgetStrings()
        assertTrue(strings.size > 50)
        strings.forEach { (name, text) -> assertFalse("$name: $text", forbidden.containsMatchIn(text)) }
    }

    @Test
    fun widgetColoursHaveNightPairsAndNoRedOrGreen() {
        fun colours(dir: String) = children(parse(File(res, "$dir/colors.xml")), "color")
            .filter { it.getAttribute("name").startsWith("widget_") }
            .associate { it.getAttribute("name") to it.textContent.trim() }
        val day = colours("values")
        val night = colours("values-night")
        assertEquals(day.keys, night.keys)
        (day.values + night.values).forEach { hex ->
            val argb = java.lang.Long.parseLong(hex.removePrefix("#"), 16)
            val r = (argb shr 16 and 0xFF) / 255.0
            val g = (argb shr 8 and 0xFF) / 255.0
            val b = (argb and 0xFF) / 255.0
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            val hue = when {
                delta == 0.0 -> 0.0
                max == r -> 60 * (((g - b) / delta).mod(6.0))
                max == g -> 60 * ((b - r) / delta + 2)
                else -> 60 * ((r - g) / delta + 4)
            }
            val saturation = if (max == 0.0) 0.0 else delta / max
            val saturated = saturation > 0.35 && max > 0.2
            val redOrGreen = hue < 30 || hue > 330 || hue in 75.0..165.0
            assertFalse("$hex reads as red or green", saturated && redOrGreen)
        }
    }

    @Test
    fun providersAreExportedAndEverythingElseIsNot() {
        val manifest = parse(File("src/main/AndroidManifest.xml"))
        val receivers = children(manifest, "receiver").associateBy { it.getAttributeNS(androidNs, "name") }
        val providers = WidgetKind.entries.map { ".widget." + WidgetUpdater.providerFor(it).simpleName }
        assertEquals(6, providers.size)
        for (name in providers) {
            val receiver = receivers.getValue(name)
            assertEquals("true", receiver.getAttributeNS(androidNs, "exported"))
            val actions = children(receiver, "action").map { it.getAttributeNS(androidNs, "name") }
            assertTrue(actions.contains("android.appwidget.action.APPWIDGET_UPDATE"))
            val meta = children(receiver, "meta-data").single()
            assertEquals("android.appwidget.provider", meta.getAttributeNS(androidNs, "name"))
        }
        assertEquals("false", receivers.getValue(".widget.WidgetActionReceiver").getAttributeNS(androidNs, "exported"))
        // Widgets add no permissions: the manifest asks for exactly what it did before.
        val permissions = children(manifest, "uses-permission").map { it.getAttributeNS(androidNs, "name") }.toSet()
        assertEquals(
            setOf(
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH",
                "android.permission.BLUETOOTH_ADMIN",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                "android.permission.POST_NOTIFICATIONS",
            ),
            permissions,
        )
    }

    @Test
    fun providerInfoIsResizablePushOnlyAndPreviewed() {
        val infos = File(res, "xml").listFiles { f -> f.name.startsWith("widget_info_") }!!.toList()
        assertEquals(6, infos.size)
        for (file in infos) {
            val info = parse(file)
            fun attr(name: String) = info.getAttributeNS(androidNs, name)
            assertEquals(file.name, "0", attr("updatePeriodMillis"))
            assertEquals(file.name, "horizontal|vertical", attr("resizeMode"))
            assertEquals(file.name, "home_screen", attr("widgetCategory"))
            listOf("initialLayout", "previewLayout", "previewImage", "description", "minResizeWidth", "minResizeHeight")
                .forEach { assertTrue("${file.name} $it", attr(it).isNotBlank()) }
            assertTrue(attr("minResizeWidth").removeSuffix("dp").toInt() <= attr("minWidth").removeSuffix("dp").toInt())
            assertTrue(attr("minResizeHeight").removeSuffix("dp").toInt() <= attr("minHeight").removeSuffix("dp").toInt())
        }
    }
}

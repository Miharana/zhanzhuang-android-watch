package app.zhanzhuang.timer.mobile.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import app.zhanzhuang.timer.model.CAPABILITY_ZHAN_ZHUANG_SYNC

class ResourceIdentityContractTest {
    @Test fun wearableCapabilityMatchesTheSharedWireIdentity() {
        val xml = File("src/main/res/xml/wearable_capabilities.xml").readText()
        assertEquals(listOf(CAPABILITY_ZHAN_ZHUANG_SYNC), Regex("<capability name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList())
    }

    @Test fun localeKeysMatch() = assertEquals(keys("values"), keys("values-zh-rCN"))

    @Test fun requiredIdentityCopyIsPresent() {
        val copy = copy("values")
        setOf(
            "app_name", "start_session", "pause_session", "resume_session", "end_session",
            "heart_rate_unavailable", "health_connect", "local_only_privacy",
        ).forEach { assertTrue(it in copy, "missing $it") }
    }

    @Test fun sessionStatusesAndPrivacyAreLocalized() {
        val english = copy("values")
        val chinese = copy("values-zh-rCN")
        setOf("status_idle", "status_starting", "status_running", "status_paused", "status_completing", "status_completed", "status_cancelled", "status_interrupted")
            .forEach { key -> assertTrue(key in english && key in chinese, "missing $key") }
        assertTrue(english.getValue("local_only_privacy").contains("paired watch"))
        assertTrue(chinese.getValue("local_only_privacy").contains("配对手表"))
    }

    @Test fun formatPlaceholdersMatchAcrossLocales() = assertEquals(
        placeholders(copy("values")),
        placeholders(copy("values-zh-rCN")),
    )

    @Test fun forbiddenClaimsAreAbsent() {
        val allCopy = listOf(copy("values"), copy("values-zh-rCN")).flatMap { it.values }.joinToString(" ").lowercase()
        listOf("治愈", "治疗", "诊断", "burn calories", "weight loss", "premium", "advertisement")
            .forEach { assertFalse(allCopy.contains(it), "forbidden claim: $it") }
    }

    @Test fun adaptiveIconContractIsPresent() {
        listOf("mipmap-anydpi-v26/ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher_round.xml", "drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_monochrome.xml", "drawable/ic_launcher_background.xml")
            .forEach { assertTrue(resource(it).isFile, "missing icon asset $it") }
        val foreground = resource("drawable/ic_launcher_foreground.xml").readText()
        assertTrue(foreground.contains("#E7D5A6") && foreground.contains("#C6A867") && foreground.contains("#A9863F") && foreground.contains("#D8C18C"))
        assertTrue(resource("drawable/ic_launcher_background.xml").readText().contains("#15110B"))
        assertTrue(foreground.contains("#FFF4D6"))
        assertTrue(foreground.contains("android:strokeLineCap=\"round\""))
        assertTrue(foreground.contains("android:strokeLineJoin=\"round\""))
        assertTrue(foreground.contains("android:fillColor=\"#00000000\""))
        assertTrue(foreground.contains("android:name=\"gold_taiji\""))
        assertTrue(foreground.contains("android:name=\"taiji_soil_half\""))
        assertTrue(foreground.contains("android:name=\"taiji_gold_dot\""))
        assertTrue(foreground.contains("android:name=\"taiji_soil_dot\""))
        assertTrue(foreground.contains("android:name=\"left_holding_arm\""))
        assertTrue(foreground.contains("android:name=\"right_holding_arm\""))
        assertTrue(foreground.contains("M46.83,47.46C40.5,50.84 40.29,56.95 44.93,59.69C46.4,60.54 47.46,59.06 47.67,57.37"))
        assertTrue(foreground.contains("M61.17,47.46C67.5,50.84 67.71,56.95 63.07,59.69C61.6,60.54 60.54,59.06 60.33,57.37"))
        assertFalse(Regex("android:name=\\\"taiji_gold_disc\\\"[^>]*android:strokeColor").containsMatchIn(foreground))
        assertFalse(foreground.contains("M50,27L58,27L60,43"))

        val monochrome = resource("drawable/ic_launcher_monochrome.xml").readText()
        assertTrue(monochrome.contains("android:name=\"taiji_mark\""))

        val svg = File("../fastlane/assets-source/icon.svg").readText()
        assertTrue(svg.contains("id=\"holding-ball-figure\""))
        assertTrue(svg.contains("id=\"gold-taiji\""))
        assertTrue(svg.contains("id=\"taiji-soil-half\""))
        assertTrue(svg.contains("id=\"taiji-gold-dot\""))
        assertTrue(svg.contains("id=\"taiji-soil-dot\""))
        assertTrue(svg.contains("id=\"taiji-disc\""))
        assertTrue(svg.contains("id=\"left-holding-arm\""))
        assertTrue(svg.contains("id=\"right-holding-arm\""))
        assertTrue(svg.contains("M222 225 C192 241 191 270 213 283 C220 287 225 280 226 272"))
        assertTrue(svg.contains("M290 225 C320 241 321 270 299 283 C292 287 287 280 286 272"))
        assertFalse(Regex("id=\\\"taiji-disc\\\"[^>]*stroke=").containsMatchIn(svg))
        assertTrue(svg.contains("stroke-linecap=\"round\""))
        assertTrue(svg.contains("stroke-linejoin=\"round\""))
        assertFalse(svg.contains("<text"))
        assertFalse(svg.contains("<linearGradient"))
    }

    private fun keys(locale: String) = copy(locale).keys

    private fun placeholders(copy: Map<String, String>) = copy.mapValues { (_, value) ->
        Regex("%\\d+\\$[ds]").findAll(value).map { it.value }.toList()
    }

    private fun copy(locale: String): Map<String, String> {
        val file = resource("$locale/strings.xml")
        assertTrue(file.isFile, "missing $locale/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return (0 until document.documentElement.childNodes.length).mapNotNull { index ->
            document.documentElement.childNodes.item(index).takeIf { it.nodeName == "string" }?.let {
                it.attributes.getNamedItem("name").nodeValue to it.textContent
            }
        }.toMap()
    }

    private fun resource(path: String) = File("src/main/res/$path")
}

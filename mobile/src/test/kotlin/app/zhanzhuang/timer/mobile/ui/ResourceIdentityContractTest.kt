package app.zhanzhuang.timer.mobile.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import app.zhanzhuang.timer.model.CAPABILITY_ZHAN_ZHUANG_PHONE

class ResourceIdentityContractTest {
    @Test fun phoneAdvertisesItsDedicatedWearCapability() {
        val xml = File("src/main/res/xml/wearable_capabilities.xml").readText()
        assertEquals(listOf(CAPABILITY_ZHAN_ZHUANG_PHONE), Regex("<capability name=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] }.toList())
        val staticCapabilities = resource("values/wear.xml").readText()
        assertEquals(listOf(CAPABILITY_ZHAN_ZHUANG_PHONE), Regex("<item>([^<]+)</item>").findAll(staticCapabilities).map { it.groupValues[1] }.toList())
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
        assertTrue(foreground.contains("#E7D5A6") && foreground.contains("#C6A867") && foreground.contains("#A9863F"))
        assertTrue(resource("drawable/ic_launcher_background.xml").readText().contains("#15110B"))
        assertTrue(foreground.contains("android:strokeLineCap=\"round\""))
        assertTrue(foreground.contains("android:strokeLineJoin=\"round\""))
        assertTrue(foreground.contains("android:fillColor=\"#00000000\""))
        assertTrue(foreground.contains("android:name=\"e_outer_rim\""))
        assertTrue(foreground.contains("android:name=\"e_taiji\""))
        assertTrue(foreground.contains("android:name=\"e_lower_left_ring\""))
        assertTrue(foreground.contains("android:name=\"e_lower_right_ring\""))
        assertTrue(foreground.contains("android:name=\"taiji_soil_half\""))
        assertTrue(foreground.contains("android:name=\"taiji_gold_dot\""))
        assertTrue(foreground.contains("android:name=\"taiji_soil_dot\""))
        assertTrue(foreground.contains("android:name=\"e_glint\""))
        assertFalse(foreground.contains("head_ring"))
        assertFalse(foreground.contains("parallel_leg"))

        val monochrome = resource("drawable/ic_launcher_monochrome.xml").readText()
        assertTrue(monochrome.contains("android:name=\"e_outer_rim\""))
        assertTrue(monochrome.contains("android:name=\"e_taiji\""))
        assertTrue(monochrome.contains("android:name=\"e_lower_left_ring\""))
        assertTrue(monochrome.contains("android:name=\"e_lower_right_ring\""))

        val svg = File("../fastlane/assets-source/icon.svg").readText()
        assertTrue(svg.contains("id=\"e-taiji-interval-mark\""))
        assertTrue(svg.contains("id=\"e-outer-rim\""))
        assertTrue(svg.contains("id=\"e-taiji\""))
        assertTrue(svg.contains("id=\"e-lower-left-ring\""))
        assertTrue(svg.contains("id=\"e-lower-right-ring\""))
        assertTrue(svg.contains("id=\"taiji-soil-half\""))
        assertTrue(svg.contains("id=\"taiji-gold-dot\""))
        assertTrue(svg.contains("id=\"taiji-soil-dot\""))
        assertTrue(svg.contains("id=\"e-glint\""))
        assertTrue(svg.contains("stroke-linecap=\"round\""))
        assertTrue(svg.contains("stroke-linejoin=\"round\""))
        assertFalse(svg.contains("standing-figure"))
        assertFalse(svg.contains("parallel-leg"))
        assertFalse(svg.contains("<text"))
        assertTrue(svg.contains("<linearGradient"))
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

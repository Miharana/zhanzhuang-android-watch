package app.zhanzhuang.timer.wear

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
        assertTrue(foreground.contains("android:name=\"head_ring\""))
        assertTrue(foreground.contains("android:name=\"standing_taiji\""))
        assertTrue(foreground.contains("android:name=\"taiji_soil_half\""))
        assertTrue(foreground.contains("android:name=\"taiji_gold_dot\""))
        assertTrue(foreground.contains("android:name=\"taiji_soil_dot\""))
        assertTrue(foreground.contains("android:name=\"left_parallel_leg\""))
        assertTrue(foreground.contains("android:name=\"right_parallel_leg\""))
        assertTrue(foreground.contains("M46.5,66L46.5,82"))
        assertTrue(foreground.contains("M61.5,66L61.5,82"))
        assertFalse(Regex("android:name=\\\"taiji_gold_disc\\\"[^>]*android:strokeColor").containsMatchIn(foreground))
        assertFalse(foreground.contains("left_holding_arm"))
        assertFalse(foreground.contains("right_holding_arm"))
        assertFalse(foreground.contains("80.79,23.84"))
        assertFalse(foreground.contains("M54,45.35L54,67.71"))
        assertFalse(foreground.contains("#FFF4D6"))

        val monochrome = resource("drawable/ic_launcher_monochrome.xml").readText()
        assertTrue(monochrome.contains("android:name=\"head_ring\""))
        assertTrue(monochrome.contains("android:name=\"standing_taiji\""))
        assertTrue(monochrome.contains("android:name=\"left_parallel_leg\""))
        assertTrue(monochrome.contains("android:name=\"right_parallel_leg\""))

        val svg = File("../fastlane/assets-source/icon.svg").readText()
        assertTrue(svg.contains("id=\"two-circle-standing-figure\""))
        assertTrue(svg.contains("id=\"head-ring\""))
        assertTrue(svg.contains("id=\"standing-taiji\""))
        assertTrue(svg.contains("id=\"taiji-soil-half\""))
        assertTrue(svg.contains("id=\"taiji-gold-dot\""))
        assertTrue(svg.contains("id=\"taiji-soil-dot\""))
        assertTrue(svg.contains("id=\"taiji-disc\""))
        assertTrue(svg.contains("id=\"left-parallel-leg\""))
        assertTrue(svg.contains("id=\"right-parallel-leg\""))
        assertTrue(svg.contains("M232 322L232 392"))
        assertTrue(svg.contains("M280 322L280 392"))
        assertFalse(Regex("id=\\\"taiji-disc\\\"[^>]*stroke=").containsMatchIn(svg))
        assertTrue(svg.contains("stroke-linecap=\"round\""))
        assertTrue(svg.contains("stroke-linejoin=\"round\""))
        assertFalse(svg.contains("left-holding-arm"))
        assertFalse(svg.contains("right-holding-arm"))
        assertFalse(svg.contains("id=\"sparkle\""))
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

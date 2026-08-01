package app.zhanzhuang.timer.wear

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceIdentityContractTest {
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
        assertTrue(foreground.contains("#E7D5A6") && foreground.contains("#C6A867") && foreground.contains("#A9863F") && foreground.contains("#D8C18C"))
        assertTrue(resource("drawable/ic_launcher_background.xml").readText().contains("#15110B"))
        assertTrue(foreground.contains("#FFF4D6"))
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

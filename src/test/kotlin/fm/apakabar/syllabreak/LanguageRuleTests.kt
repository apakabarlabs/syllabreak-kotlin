package fm.apakabar.syllabreak

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

class LanguageRuleTests {
    @Serializable
    data class TestData(
        val tests: List<TestCase>,
        @SerialName("mapping_tests") val mappingTests: List<MappingTestCase>,
        @SerialName("geminate_tests") val geminateTests: List<GeminateTestCase>,
    )

    @Serializable
    data class TestCase(
        val name: String,
        val values: List<String>,
        val expected: List<String>,
    )

    @Serializable
    data class MappingTestCase(
        val name: String,
        val mapping: Map<String, String>,
        val expected: List<MappingEntry>,
    )

    @Serializable
    data class MappingEntry(
        val key: String,
        val value: String,
    )

    @Serializable
    data class GeminateTestCase(
        val name: String,
        val rule: GeminateRule,
        val word: String,
        val expanded: String,
        val spans: List<ExpectedSpan>,
    )

    @Serializable
    data class GeminateRule(
        val lang: String,
        val vowels: String,
        val consonants: String,
        @SerialName("geminate_digraphs") val geminateDigraphs: Map<String, String>,
    ) {
        fun languageRule() =
            LanguageRule(
                lang = lang,
                vowels = vowels.toSet(),
                consonants = consonants.toSet(),
                clustersKeepNext = emptySet(),
                dontSplitDigraphs = emptySet(),
                digraphVowels = emptySet(),
                syllabicConsonants = emptySet(),
                modifiersAttachLeft = emptySet(),
                modifiersSeparators = emptySet(),
                geminateDigraphs = augmentMapping(geminateDigraphs),
            )
    }

    @Serializable
    data class ExpectedSpan(
        val start: Int,
        val length: Int,
        val compact: String,
    )

    @TestFactory
    fun augmentSetTests(): Collection<DynamicTest> {
        val input =
            requireNotNull(this::class.java.getResourceAsStream("/language_rule_tests.yaml")) {
                "Cannot load language_rule_tests.yaml"
            }
        val data = Yaml.default.decodeFromString(TestData.serializer(), input.reader().readText())

        return data.tests.map { case ->
            DynamicTest.dynamicTest(case.name) {
                assertEquals(case.expected.toSet(), augmentSet(case.values))
            }
        }
    }

    @TestFactory
    fun augmentMappingTests(): Collection<DynamicTest> {
        val input =
            requireNotNull(this::class.java.getResourceAsStream("/language_rule_tests.yaml")) {
                "Cannot load language_rule_tests.yaml"
            }
        val data = Yaml.default.decodeFromString(TestData.serializer(), input.reader().readText())

        return data.mappingTests.map { case ->
            DynamicTest.dynamicTest(case.name) {
                val actual = augmentMapping(case.mapping)
                case.expected.forEach { expected ->
                    assertEquals(expected.value, actual[expected.key])
                }
            }
        }
    }

    @TestFactory
    fun expandGeminateDigraphTests(): Collection<DynamicTest> {
        val input =
            requireNotNull(this::class.java.getResourceAsStream("/language_rule_tests.yaml")) {
                "Cannot load language_rule_tests.yaml"
            }
        val data = Yaml.default.decodeFromString(TestData.serializer(), input.reader().readText())

        return data.geminateTests.map { case ->
            DynamicTest.dynamicTest(case.name) {
                val (expanded, spans) = case.rule.languageRule().expandGeminateDigraphs(case.word)
                assertEquals(case.expanded, expanded)
                assertEquals(case.spans.size, spans.size)
                spans.zip(case.spans).forEach { (span, expected) ->
                    assertEquals(expected.start, span.start)
                    assertEquals(expected.length, span.length)
                    assertEquals(expected.compact, span.compactOriginal)
                }
            }
        }
    }
}

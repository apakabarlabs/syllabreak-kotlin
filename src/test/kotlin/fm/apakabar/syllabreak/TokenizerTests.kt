package fm.apakabar.syllabreak

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.text.Normalizer
import kotlin.test.assertEquals

class TokenizerTests {
    @Serializable
    data class TestData(val tests: List<TestCase>)

    @Serializable
    data class TestCase(
        val name: String,
        val rule: TestRule,
        val text: String,
        val normalization: String? = null,
        val tokens: List<ExpectedToken>,
    )

    @Serializable
    data class TestRule(
        val lang: String,
        val vowels: String,
        val consonants: String,
        @SerialName("clusters_keep_next") val clustersKeepNext: List<String> = emptyList(),
        @SerialName("dont_split_digraphs") val dontSplitDigraphs: List<String> = emptyList(),
        @SerialName("digraph_vowels") val digraphVowels: List<String> = emptyList(),
        @SerialName("syllabic_consonants") val syllabicConsonants: String = "",
        @SerialName("modifiers_attach_left") val modifiersAttachLeft: String = "",
        @SerialName("modifiers_separators") val modifiersSeparators: String = "",
    ) {
        fun languageRule() =
            LanguageRule(
                lang = lang,
                vowels = vowels.toSet(),
                consonants = consonants.toSet(),
                clustersKeepNext = clustersKeepNext.toSet(),
                dontSplitDigraphs = dontSplitDigraphs.toSet(),
                digraphVowels = digraphVowels.toSet(),
                syllabicConsonants = syllabicConsonants.toSet(),
                modifiersAttachLeft = modifiersAttachLeft.toSet(),
                modifiersSeparators = modifiersSeparators.toSet(),
            )
    }

    @Serializable
    data class ExpectedToken(
        val surface: String,
        @SerialName("class") val tokenClass: String,
        val modifier: Boolean = false,
        val start: Int,
        val end: Int,
    )

    @TestFactory
    fun tokenizerTests(): Collection<DynamicTest> {
        val input =
            requireNotNull(this::class.java.getResourceAsStream("/tokenizer_tests.yaml")) {
                "Cannot load tokenizer_tests.yaml"
            }
        val data = Yaml.default.decodeFromString(TestData.serializer(), input.reader().readText())

        return data.tests.map { case ->
            DynamicTest.dynamicTest(case.name) {
                val text =
                    if (case.normalization == "nfd") {
                        Normalizer.normalize(case.text, Normalizer.Form.NFD)
                    } else {
                        case.text
                    }
                val actual = SyllableTokenizer(text, case.rule.languageRule()).tokenize()

                assertEquals(case.tokens.size, actual.size)
                actual.zip(case.tokens).forEach { (token, expected) ->
                    assertEquals(expected.surface, token.surface)
                    assertEquals(expected.tokenClass, token.tokenClass.name.lowercase())
                    assertEquals(expected.modifier, token.isModifier)
                    assertEquals(expected.start, token.startIdx)
                    assertEquals(expected.end, token.endIdx)
                }
            }
        }
    }
}

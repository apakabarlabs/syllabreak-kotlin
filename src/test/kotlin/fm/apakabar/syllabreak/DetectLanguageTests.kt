package fm.apakabar.syllabreak

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class DetectLanguageTests {
    @Serializable
    data class TestData(val tests: List<TestSection>)

    @Serializable
    data class TestSection(val lang: String? = null, val cases: List<String>)

    @TestFactory
    fun detectLanguageTests(): Collection<DynamicTest> {
        val input =
            requireNotNull(
                this::class.java.getResourceAsStream("/detect_language_tests.yaml"),
            ) { "Cannot load detect_language_tests.yaml" }

        val text = input.use { it.readBytes().decodeToString() }
        val data = Yaml.default.decodeFromString(TestData.serializer(), text)
        val tests = mutableListOf<DynamicTest>()
        val syllabreak = Syllabreak()

        for (section in data.tests) {
            for (text in section.cases) {
                val testName =
                    if (section.lang != null) {
                        "Detect: $text -> should contain ${section.lang}"
                    } else {
                        "Detect: $text -> should return empty"
                    }
                tests.add(
                    DynamicTest.dynamicTest(testName) {
                        val result = syllabreak.detectLanguage(text)
                        if (section.lang != null) {
                            assert(section.lang in result) { "Failed for '$text': got $result, expected to contain ${section.lang}" }
                        } else {
                            assert(result.isEmpty()) { "Failed for '$text': got $result, expected empty list" }
                        }
                    },
                )
            }
        }

        return tests
    }
}

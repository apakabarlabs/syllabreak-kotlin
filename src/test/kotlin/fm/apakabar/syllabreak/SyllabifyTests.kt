package fm.apakabar.syllabreak

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

class SyllabifyTests {
    @Serializable
    data class TestData(val tests: List<TestSection>)

    @Serializable
    data class TestSection(
        val section: String,
        val lang: String? = null,
        val cases: List<TestCase>,
    )

    @Serializable
    data class TestCase(
        val text: String,
        val want: String,
    )

    @TestFactory
    fun syllabifyTests(): Collection<DynamicTest> {
        val input =
            requireNotNull(
                this::class.java.getResourceAsStream("/syllabify_tests.yaml"),
            ) { "Cannot load syllabify_tests.yaml" }

        val text = input.use { it.readBytes().decodeToString() }
        val data = Yaml.default.decodeFromString(TestData.serializer(), text)
        val tests = mutableListOf<DynamicTest>()
        val syllabifier = Syllabreak("-") // Use regular hyphen for tests

        for (section in data.tests) {
            for (case in section.cases) {
                tests.add(
                    DynamicTest.dynamicTest("[${section.section}] ${case.text} -> ${case.want}") {
                        val result =
                            if (section.lang != null) {
                                syllabifier.syllabify(case.text, section.lang)
                            } else {
                                syllabifier.syllabify(case.text)
                            }
                        assertEquals(case.want, result, "Failed for '${case.text}': got '$result', want '${case.want}'")
                    },
                )
            }
        }

        return tests
    }
}

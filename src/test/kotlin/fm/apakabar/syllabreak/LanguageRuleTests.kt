package fm.apakabar.syllabreak

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

class LanguageRuleTests {
    @Serializable
    data class TestData(val tests: List<TestCase>)

    @Serializable
    data class TestCase(
        val name: String,
        val values: List<String>,
        val expected: List<String>,
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
}

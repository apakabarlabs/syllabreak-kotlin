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
}

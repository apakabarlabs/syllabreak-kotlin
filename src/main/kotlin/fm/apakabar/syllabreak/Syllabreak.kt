package fm.apakabar.syllabreak

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Main class for syllabification and language detection.
 *
 * Provides accurate and deterministic hyphenation for multiple languages
 * without relying on dictionaries. Uses rule-based approach for syllable
 * boundary detection.
 *
 * @property softHyphen The character to use for syllable separation.
 *                      Defaults to Unicode soft hyphen (U+00AD).
 *
 * @constructor Creates a Syllabreak instance with the specified soft hyphen.
 *
 * Example usage:
 * ```kotlin
 * val syllabreak = Syllabreak("-")
 * println(syllabreak.syllabify("hello"))        // "hel-lo"
 * println(syllabreak.detectLanguage("привет"))  // ["rus"]
 * ```
 */
class Syllabreak
    @JvmOverloads
    constructor(
        private val softHyphen: String = "\u00AD",
    ) {
        private val metaRule: MetaRule = loadRules()

        private fun loadRules(): MetaRule {
            val mapper = ObjectMapper(YAMLFactory()).registerModule(kotlinModule())
            val input =
                requireNotNull(
                    this::class.java.getResourceAsStream("/rules.yaml"),
                ) { "Cannot load rules.yaml" }

            val data: RulesYaml = input.use { mapper.readValue(it) }
            val rules =
                data.rules.map { ruleYaml ->
                    LanguageRule(
                        lang = ruleYaml.lang,
                        vowels = ruleYaml.vowels.toSet(),
                        consonants = ruleYaml.consonants.toSet(),
                        sonorants = ruleYaml.sonorants.toSet(),
                        clustersKeepNext = (ruleYaml.clustersKeepNext ?: emptyList()).toSet(),
                        dontSplitDigraphs = (ruleYaml.dontSplitDigraphs ?: emptyList()).toSet(),
                        digraphVowels = (ruleYaml.digraphVowels ?: emptyList()).toSet(),
                        glides = (ruleYaml.glides ?: "").toSet(),
                        syllabicConsonants = (ruleYaml.syllabicConsonants ?: "").toSet(),
                        modifiersAttachLeft = (ruleYaml.modifiersAttachLeft ?: "").toSet(),
                        modifiersAttachRight = (ruleYaml.modifiersAttachRight ?: "").toSet(),
                        modifiersSeparators = (ruleYaml.modifiersSeparators ?: "").toSet(),
                        clustersOnlyAfterLong = (ruleYaml.clustersOnlyAfterLong ?: emptyList()).toSet(),
                        splitHiatus = ruleYaml.splitHiatus ?: false,
                        finalSemivowels = (ruleYaml.finalSemivowels ?: "").toSet(),
                        finalSequencesKeep = (ruleYaml.finalSequencesKeep ?: emptyList()).toSet(),
                        suffixesBreakVre = (ruleYaml.suffixesBreakVre ?: emptyList()).toSet(),
                        suffixesKeepVre = (ruleYaml.suffixesKeepVre ?: emptyList()).toSet(),
                        exceptions = ruleYaml.exceptions ?: emptyMap(),
                    )
                }
            return MetaRule(rules)
        }

        /**
         * Detects possible languages for the given text.
         *
         * Analyzes the characters in the text and returns a list of language codes
         * that match the text, ordered by confidence (best match first).
         *
         * @param text The text to analyze
         * @return List of language codes (e.g., ["eng"], ["rus"], ["srp-cyrl"])
         *         Empty list if no language matches or text is empty
         *
         * Example:
         * ```kotlin
         * val languages = syllabreak.detectLanguage("hello")
         * // Returns: ["eng"]
         * ```
         */
        fun detectLanguage(text: String): List<String> {
            val matchingRules = metaRule.findMatches(text)
            return matchingRules.map { it.lang }
        }

        /**
         * Codes of every language the loaded rules cover, in rule-file order.
         */
        fun supportedLanguages(): List<String> = metaRule.rules.map { it.lang }

        /**
         * Syllabifies the given text by inserting soft hyphens at syllable boundaries.
         *
         * @param text The text to syllabify
         * @param lang Optional language code to force specific language rules.
         *             If null, automatically detects the language.
         * @return The text with soft hyphens inserted at syllable boundaries
         * @throws IllegalArgumentException if the specified language is not supported
         *
         * Example:
         * ```kotlin
         * // Auto-detect language
         * val result = syllabreak.syllabify("hello")
         * // Returns: "hel­lo"
         *
         * // Force specific language
         * val result = syllabreak.syllabify("problem", "eng")
         * // Returns: "pro­blem"
         * ```
         */
        @JvmOverloads
        fun syllabify(
            text: String,
            lang: String? = null,
        ): String {
            if (text.isEmpty()) return text

            val rule =
                if (lang != null) {
                    getRuleByLang(lang) ?: return text
                } else {
                    autoDetectRule(text) ?: return text
                }

            val syllabifier = WordSyllabifier(rule)
            val tokenizer = Tokenizer(rule)
            val tokens = tokenizer.tokenize(text)

            return tokens.joinToString("") { token ->
                when (token.type) {
                    TokenType.WORD -> syllabifier.syllabifyWord(token.text, softHyphen)
                    else -> token.text
                }
            }
        }

        private fun autoDetectRule(text: String): LanguageRule? {
            val matchingRules = metaRule.findMatches(text)
            return matchingRules.firstOrNull()
        }

        private fun getRuleByLang(lang: String): LanguageRule? {
            return metaRule.rules.find { it.lang == lang }
        }
    }

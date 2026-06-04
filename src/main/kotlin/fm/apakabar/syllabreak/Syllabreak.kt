package fm.apakabar.syllabreak

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.text.Normalizer

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
                        clustersKeepNext = augmentSet(ruleYaml.clustersKeepNext),
                        trailingOnsets = augmentSet(ruleYaml.trailingOnsets),
                        dontSplitDigraphs = augmentSet(ruleYaml.dontSplitDigraphs),
                        digraphVowels = augmentSet(ruleYaml.digraphVowels),
                        vowelGlides = (ruleYaml.vowelGlides ?: "").toSet(),
                        syllabicConsonants = (ruleYaml.syllabicConsonants ?: "").toSet(),
                        modifiersAttachLeft = (ruleYaml.modifiersAttachLeft ?: "").toSet(),
                        modifiersSeparators = (ruleYaml.modifiersSeparators ?: "").toSet(),
                        clustersOnlyAfterLong = augmentSet(ruleYaml.clustersOnlyAfterLong),
                        splitHiatus = ruleYaml.splitHiatus ?: false,
                        finalSemivowels = (ruleYaml.finalSemivowels ?: "").toSet(),
                        finalSequencesKeep = augmentSet(ruleYaml.finalSequencesKeep),
                        suffixesBreakVre = augmentSet(ruleYaml.suffixesBreakVre),
                        suffixesKeepVre = augmentSet(ruleYaml.suffixesKeepVre),
                        exceptions = augmentMapping(ruleYaml.exceptions),
                        geminateDigraphs = augmentMapping(ruleYaml.geminateDigraphs),
                    )
                }
            return MetaRule(rules)
        }

        // Multi-character entries are stored as the union of their NFC form
        // (as written in rules.yaml) and their NFD decomposition, so the
        // tokenizer can match against either form of input.
        private fun augmentSet(values: List<String>?): Set<String> {
            if (values == null) return emptySet()
            val result = HashSet<String>(values.size * 2)
            for (value in values) {
                result.add(value)
                result.add(Normalizer.normalize(value, Normalizer.Form.NFD))
            }
            return result
        }

        private fun augmentMapping(mapping: Map<String, String>?): Map<String, String> {
            if (mapping == null) return emptyMap()
            val result = HashMap<String, String>(mapping.size * 2)
            for ((key, value) in mapping) {
                result[key] = value
                result[Normalizer.normalize(key, Normalizer.Form.NFD)] =
                    Normalizer.normalize(value, Normalizer.Form.NFD)
            }
            return result
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
            // Detect on NFC-normalised text so precomposed letters (Polish ą,
            // deu ä, polytonic Greek ἤ …) discriminate via each rule's
            // unique_chars set, whatever form the caller hands us.
            val matchingRules = metaRule.findMatches(Normalizer.normalize(text, Normalizer.Form.NFC))
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
                    autoDetectRule(Normalizer.normalize(text, Normalizer.Form.NFC)) ?: return text
                }

            // Internally we work on the NFD form so combining marks
            // (polytonic Greek, BCMS с́, etc.) are visible as separate
            // codepoints. Rule fields are augmented with NFD forms at load
            // time and the tokenizer auto-attaches Mn marks, so the
            // algorithm runs naturally on the decomposed stream. The final
            // result is re-normalised to NFC so callers see the canonical
            // user-visible form.
            val nfdText = Normalizer.normalize(text, Normalizer.Form.NFD)

            val syllabifier = WordSyllabifier(rule)
            val tokenizer = Tokenizer(rule)
            val tokens = tokenizer.tokenize(nfdText)

            val output =
                tokens.joinToString("") { token ->
                    when (token.type) {
                        TokenType.WORD -> syllabifier.syllabifyWord(token.text, softHyphen)
                        else -> token.text
                    }
                }
            return Normalizer.normalize(output, Normalizer.Form.NFC)
        }

        private fun autoDetectRule(text: String): LanguageRule? {
            val matchingRules = metaRule.findMatches(text)
            return matchingRules.firstOrNull()
        }

        private fun getRuleByLang(lang: String): LanguageRule? {
            return metaRule.rules.find { it.lang == lang }
        }
    }

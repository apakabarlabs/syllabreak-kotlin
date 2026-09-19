package fm.apakabar.syllabreak

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.text.Normalizer

internal fun augmentSet(values: List<String>?): Set<String> {
    if (values == null) return emptySet()
    val result = HashSet<String>(values.size * 2)
    for (value in values) {
        result.add(value)
        result.add(Normalizer.normalize(value, Normalizer.Form.NFD))
    }
    return result
}

internal fun augmentMapping(mapping: Map<String, String>?): Map<String, String> {
    if (mapping == null) return emptyMap()
    val result = HashMap<String, String>(mapping.size * 2)
    for ((key, value) in mapping) {
        result[key] = value
        result[Normalizer.normalize(key, Normalizer.Form.NFD)] =
            Normalizer.normalize(value, Normalizer.Form.NFD)
    }
    return result
}

internal fun validateVowelNucleusRules(
    entries: List<VowelNucleusRuleYaml>?,
    vowels: Set<Char>,
): List<LanguageRule.VowelNucleusRule> {
    return entries.orEmpty().map { entry ->
        val suffix = Normalizer.normalize(entry.suffix, Normalizer.Form.NFD)
        require(
            entry.vowelLength > 0 &&
                entry.vowelOffset in suffix.indices &&
                entry.vowelOffset + entry.vowelLength <= suffix.length,
        ) { "invalid vowel offset for nucleus rule: $suffix" }
        require(suffix.substring(entry.vowelOffset, entry.vowelOffset + entry.vowelLength).all { it in vowels }) {
            "nucleus rule target is not a vowel: $suffix"
        }
        require(entry.outcome in setOf("preserve", "silent")) { "invalid nucleus rule outcome: ${entry.outcome}" }
        val predecessors =
            entry.precededBy.orEmpty().map { predecessor ->
                val normalized = Normalizer.normalize(predecessor, Normalizer.Form.NFD)
                require(normalized.length == 1) { "nucleus rule predecessor must be one character: $suffix" }
                normalized.single()
            }.toSet()
        require(entry.precededByClass in setOf(null, "consonant", "vowel")) {
            "invalid nucleus rule predecessor class: ${entry.precededByClass}"
        }
        LanguageRule.VowelNucleusRule(
            suffix = suffix,
            vowelOffset = entry.vowelOffset,
            vowelLength = entry.vowelLength,
            outcome = entry.outcome,
            words = entry.words.orEmpty().map { Normalizer.normalize(it, Normalizer.Form.NFD).lowercase() }.toSet(),
            precededBy = predecessors,
            precededByClass = entry.precededByClass,
        )
    }
}

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
            val yaml = Yaml(configuration = YamlConfiguration(anchorsAndAliases = AnchorsAndAliases.Permitted()))
            val input =
                requireNotNull(
                    this::class.java.getResourceAsStream("/rules.yaml"),
                ) { "Cannot load rules.yaml" }

            val text = input.use { it.readBytes().decodeToString() }
            val data = yaml.decodeFromString(RulesYaml.serializer(), text)
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
                        vowelNucleusRules =
                            validateVowelNucleusRules(ruleYaml.vowelNucleusRules, ruleYaml.vowels.toSet()),
                        exceptions = augmentMapping(ruleYaml.exceptions),
                        geminateDigraphs = augmentMapping(ruleYaml.geminateDigraphs),
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

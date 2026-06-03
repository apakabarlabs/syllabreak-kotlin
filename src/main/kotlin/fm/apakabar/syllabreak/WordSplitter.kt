package fm.apakabar.syllabreak

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Per-language sentence → words splitter. Mirrors the Python and Swift ports
 * through the shared word_split_rules.yaml and word_split_tests.yaml.
 *
 * Two modes, configured in `/word_split_rules.yaml`:
 *
 *  - **default** — Latin / Cyrillic / Arabic / Hebrew / Hindi etc. A word is
 *    one or more Unicode letters/marks/digits, optionally joined by an
 *    apostrophe (straight or curly) or a hyphen. Combining marks attach to
 *    the preceding letter via `\p{M}`.
 *  - **cjk** — `cmn`, `jpn`, `kor`. Each Han / Hiragana / Katakana / Hangul
 *    character is its own word; Latin/digit runs stay together so
 *    "iPhoneを使う" yields ["iPhone", "を", "使", "う"].
 */
class WordSplitter {
    private data class RulesEntry(val lang: String, val mode: String)

    private data class RulesData(val rules: List<RulesEntry>)

    private val modes: Map<String, String>

    init {
        val mapper = ObjectMapper(YAMLFactory()).registerModule(kotlinModule())
        val input =
            requireNotNull(
                this::class.java.getResourceAsStream("/word_split_rules.yaml"),
            ) { "Cannot load word_split_rules.yaml" }
        val data: RulesData = input.use { mapper.readValue(it) }
        modes = data.rules.associate { it.lang to it.mode }
    }

    fun split(
        text: String,
        lang: String,
    ): List<String> = findRanges(text, lang).map { text.substring(it.first, it.last) }

    /**
     * Word ranges as (start, end) character offsets — needed by clients that
     * highlight or annotate positions in the original text where re-searching
     * the surface form would be ambiguous on repeats.
     */
    fun findRanges(
        text: String,
        lang: String,
    ): List<IntRange> {
        val regex = if (modes[lang] == "cjk") cjkRegex else defaultRegex
        return regex.findAll(text).map { it.range.first..(it.range.last + 1) }.toList()
    }

    companion object {
        // \p{L}\p{M}\p{Nd} via UNICODE_CHARACTER_CLASS (RegexOption.UNICODE_CASE
        // is not enough; Java needs explicit flag for \p{L}). Kotlin Regex uses
        // java.util.regex.Pattern under the hood.
        private val defaultRegex =
            Regex(
                "[\\p{L}\\p{M}\\p{Nd}]+(?:['’\\-][\\p{L}\\p{M}\\p{Nd}]+)*",
                RegexOption.UNIX_LINES,
            )

        // CJK char ranges as literals (Han Unified + Extension A + Compat,
        // Hiragana, Katakana, Hangul Syllables). Latin/digit alternative is
        // first so "iPhoneを使う" keeps "iPhone" intact.
        private val cjkRegex =
            Regex(
                "[A-Za-z0-9]+(?:['’\\-][A-Za-z0-9]+)*" +
                    "|[一-鿿㐀-䶿豈-﫿" +
                    "぀-ゟ゠-ヿ가-힯]",
            )
    }
}

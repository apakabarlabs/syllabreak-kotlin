package fm.apakabar.syllabreak

data class LanguageRule(
    val lang: String,
    val vowels: Set<Char>,
    val consonants: Set<Char>,
    val sonorants: Set<Char>,
    val clustersKeepNext: Set<String>,
    // trailing_onsets — onsets valid ONLY in trailing position of a 3+
    // consonant cluster. Used for Dutch where s+stop splits as VC-CV in
    // a plain 2-cons cluster (kas-teel) but stays together as the next
    // syllable's onset when preceded by another consonant (ven-ster,
    // in-dus-trie). Checked alongside clustersKeepNext inside the 3+
    // cluster boundary decision.
    val trailingOnsets: Set<String> = emptySet(),
    val dontSplitDigraphs: Set<String>,
    val digraphVowels: Set<String>,
    val glides: Set<Char>,
    // Letters that are a vowel (nucleus) after a consonant / word start but a
    // glide consonant after a vowel — Kazakh у/и (ту-ыс vs да-уа).
    val vowelGlides: Set<Char> = emptySet(),
    val syllabicConsonants: Set<Char>,
    val modifiersAttachLeft: Set<Char>,
    val modifiersSeparators: Set<Char>,
    val clustersOnlyAfterLong: Set<String> = emptySet(),
    val splitHiatus: Boolean = false,
    val finalSemivowels: Set<Char> = emptySet(),
    val finalSequencesKeep: Set<String> = emptySet(),
    val suffixesBreakVre: Set<String> = emptySet(),
    val suffixesKeepVre: Set<String> = emptySet(),
    // Lowercased word -> hyphen-marked split. Overrides the algorithm for
    // individual words that escape the general rules (e.g. BCMS "dvije",
    // "prije" — graphic -ije- not from jat, see Matešić 2015 rule P11).
    val exceptions: Map<String, String> = emptyMap(),
    // Compact-form digraph geminates -> expanded form, applied before
    // tokenisation. Hungarian writes long double digraphs in a simplified
    // form (ssz=sz+sz, ggy=gy+gy, ...) but at a line break both halves
    // are restored in full (asz-szony, meny-nyi).
    val geminateDigraphs: Map<String, String> = emptyMap(),
    internal val uniqueChars: Set<Char> = emptySet(),
    internal val meta: MetaRule? = null,
) {
    data class GeminateSpan(val start: Int, val length: Int, val compactOriginal: String)

    val allChars: Set<Char> =
        vowels + consonants + modifiersAttachLeft + modifiersSeparators

    fun calculateMatchScore(text: String): Double {
        if (text.isEmpty()) return 0.0

        val cleanText = text.lowercase().filter { it.isLetter() }
        if (cleanText.isEmpty()) return 0.0

        var matches = 0
        var total = 0

        for (char in cleanText) {
            if (char in allChars) {
                matches++
            }
            total++
        }

        return if (total > 0) matches.toDouble() / total else 0.0
    }

    /**
     * Expand compact-form digraph geminates (Hungarian ssz, ggy, ...).
     *
     * Returns the expanded string and a list of spans. Each span carries
     * (start_in_expanded, length_in_expanded, compact_original_text); the
     * spans let the caller decide whether to render the expanded form (when
     * a boundary falls inside the span) or restore the compact form
     * (when the geminate is not actually split).
     */
    fun expandGeminateDigraphs(word: String): Pair<String, List<GeminateSpan>> {
        if (geminateDigraphs.isEmpty()) return word to emptyList()
        val patterns = geminateDigraphs.entries.sortedByDescending { it.key.length }
        val wordLower = word.lowercase()
        val result = StringBuilder()
        val spans = mutableListOf<GeminateSpan>()
        var i = 0
        var expandedPos = 0
        while (i < word.length) {
            var matched = false
            for ((short, long) in patterns) {
                if (i + short.length <= word.length && wordLower.substring(i, i + short.length) == short) {
                    val originalCompact = word.substring(i, i + short.length)
                    val expansion =
                        when {
                            originalCompact == originalCompact.uppercase() -> long.uppercase()
                            originalCompact[0].isUpperCase() -> long[0].uppercaseChar() + long.substring(1).lowercase()
                            else -> long
                        }
                    spans.add(GeminateSpan(expandedPos, expansion.length, originalCompact))
                    result.append(expansion)
                    expandedPos += expansion.length
                    i += short.length
                    matched = true
                    break
                }
            }
            if (!matched) {
                result.append(word[i])
                expandedPos++
                i++
            }
        }
        return result.toString() to spans
    }
}

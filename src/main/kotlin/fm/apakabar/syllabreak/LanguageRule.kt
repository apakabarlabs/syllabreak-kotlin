package fm.apakabar.syllabreak

data class LanguageRule(
    val lang: String,
    val vowels: Set<Char>,
    val consonants: Set<Char>,
    val sonorants: Set<Char>,
    val clustersKeepNext: Set<String>,
    val dontSplitDigraphs: Set<String>,
    val digraphVowels: Set<String>,
    val glides: Set<Char>,
    val syllabicConsonants: Set<Char>,
    val modifiersAttachLeft: Set<Char>,
    val modifiersAttachRight: Set<Char>,
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
    internal val uniqueChars: Set<Char> = emptySet(),
    internal val meta: MetaRule? = null,
) {
    val allChars: Set<Char> =
        vowels + consonants + modifiersAttachLeft +
            modifiersAttachRight + modifiersSeparators

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
}

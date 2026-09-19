package fm.apakabar.syllabreak

data class LanguageRule(
    val lang: String,
    val vowels: Set<Char>,
    val consonants: Set<Char>,
    val clustersKeepNext: Set<String>,
    val trailingOnsets: Set<String> = emptySet(),
    val dontSplitDigraphs: Set<String>,
    val digraphVowels: Set<String>,
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
    val exceptions: Map<String, String> = emptyMap(),
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

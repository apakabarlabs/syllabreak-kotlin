package fm.apakabar.syllabreak

class Tokenizer(private val rule: LanguageRule) {
    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val wordPattern = Regex("[\\p{L}\\p{M}]+")

        var lastEnd = 0
        wordPattern.findAll(text).forEach { match ->
            // Add non-word token if there's text before this word
            if (match.range.first > lastEnd) {
                tokens.add(
                    Token(
                        text = text.substring(lastEnd, match.range.first),
                        type = TokenType.NON_WORD,
                    ),
                )
            }

            // Add word token
            tokens.add(
                Token(
                    text = match.value,
                    type = TokenType.WORD,
                ),
            )

            lastEnd = match.range.last + 1
        }

        // Add remaining non-word text if any
        if (lastEnd < text.length) {
            tokens.add(
                Token(
                    text = text.substring(lastEnd),
                    type = TokenType.NON_WORD,
                ),
            )
        }

        return tokens
    }
}

class SyllableTokenizer(
    private val word: String,
    private val rule: LanguageRule,
) {
    private val wordLower = word.lowercase()
    private val tokens = mutableListOf<SyllableToken>()
    private var pos = 0

    fun tokenize(): List<SyllableToken> {
        while (pos < word.length) {
            when {
                tryMatchLeftModifier() -> continue
                tryMatchSeparator() -> continue
                tryMatchConsonantDigraph() -> continue
                tryMatchVowelDigraph() -> continue
                else -> addSingleCharacterToken()
            }
        }
        return tokens
    }

    private fun tryMatchLeftModifier(): Boolean {
        val char = wordLower[pos]
        // Explicit list from the rule, plus any Unicode nonspacing mark.
        // The Mn fallback covers polytonic Greek breathings / accents /
        // iota subscript and any other combining mark without needing to
        // enumerate them per language.
        val attaches = char in rule.modifiersAttachLeft || isNonspacingMark(char)
        if (!attaches) return false

        if (tokens.isNotEmpty()) {
            tokens.last().apply {
                surface += word[pos]
                endIdx = pos + 1
                isModifier = true
            }
        } else {
            tokens.add(
                SyllableToken(
                    surface = word[pos].toString(),
                    tokenClass = TokenClass.OTHER,
                    isModifier = true,
                    startIdx = pos,
                    endIdx = pos + 1,
                ),
            )
        }
        pos++
        return true
    }

    private fun tryMatchSeparator(): Boolean {
        val char = wordLower[pos]
        if (char !in rule.modifiersSeparators) return false

        tokens.add(
            SyllableToken(
                surface = word[pos].toString(),
                tokenClass = TokenClass.SEPARATOR,
                startIdx = pos,
                endIdx = pos + 1,
            ),
        )
        pos++
        return true
    }

    private fun tryMatchConsonantDigraph(): Boolean = tryMatchDigraph(rule.dontSplitDigraphs, TokenClass.CONSONANT)

    private fun tryMatchVowelDigraph(): Boolean = tryMatchDigraph(rule.digraphVowels, TokenClass.VOWEL)

    private fun tryMatchDigraph(
        source: Set<String>,
        tokenClass: TokenClass,
    ): Boolean {
        // For each candidate length (3, 2, 1) try the Mn-skipping match
        // first, then the direct substring match. The Mn-skip path
        // composes the next N base letters skipping combining marks, so
        // it can cover more codepoints than a direct length-N substring
        // — necessary for Vietnamese triphthongs like yêu (y + ê + u),
        // where Mn-skip-3 matches the "yeu" base entry across 4
        // codepoints, while direct-3 would catch the shorter "ye◌̂"
        // first.
        //
        // The direct path is kept as a fallback within each length for
        // entries whose marks sit on a vowel that participates in the
        // digraph itself (German "üh" = u + ◌̈ + h).
        val positions = scanBases()
        val bases = if (positions.isNotEmpty()) basesAtPositions(positions) else emptyList()
        for (length in listOf(3, 2, 1)) {
            if (bases.size >= length) {
                val candidate = bases.take(length).joinToString("")
                if (candidate in source) {
                    val end = positions[length - 1]
                    if (!diaeresisVetoesAt(end)) {
                        addDigraphToken(end, tokenClass)
                        pos = end
                        return true
                    }
                }
            }
            val end = pos + length
            if (end > word.length) continue
            val substr = wordLower.substring(pos, end)
            if (substr in source && !diaeresisVetoesAt(end)) {
                addDigraphToken(end, tokenClass)
                pos = end
                return true
            }
        }
        return false
    }

    private fun addDigraphToken(
        end: Int,
        tokenClass: TokenClass,
    ) {
        tokens.add(
            SyllableToken(
                surface = word.substring(pos, end),
                tokenClass = tokenClass,
                startIdx = pos,
                endIdx = end,
            ),
        )
    }

    private fun scanBases(): List<Int> {
        // End-positions of up to 3 upcoming base letters, skipping Mn marks
        // between them. word.substring(pos, positions[k-1]) is the surface
        // for a k-base match including its intervening marks.
        val positions = ArrayList<Int>(3)
        var p = pos
        while (p < word.length && positions.size < 3) {
            if (isNonspacingMark(wordLower[p])) {
                p++
                continue
            }
            positions.add(p + 1)
            p++
        }
        return positions
    }

    private fun basesAtPositions(positions: List<Int>): List<Char> {
        val chars = ArrayList<Char>(positions.size)
        for ((idx, end) in positions.withIndex()) {
            val start = if (idx == 0) pos else positions[idx - 1]
            for (q in (end - 1) downTo start) {
                if (!isNonspacingMark(wordLower[q])) {
                    chars.add(wordLower[q])
                    break
                }
            }
        }
        return chars
    }

    private fun diaeresisVetoesAt(endPos: Int): Boolean {
        // Diaeresis (U+0308) on the closing base of a candidate digraph
        // signals hiatus, not a diphthong (αϊ / Μαΐου / naïf).
        for (p in endPos until word.length) {
            val ch = wordLower[p]
            if (!isNonspacingMark(ch)) return false
            if (ch == COMBINING_DIAERESIS) return true
        }
        return false
    }

    private fun addSingleCharacterToken() {
        val char = wordLower[pos]
        val tokenClass =
            when {
                char in rule.vowels -> TokenClass.VOWEL
                char in rule.consonants -> TokenClass.CONSONANT
                else -> TokenClass.OTHER
            }

        val isGlide = char in rule.glides

        tokens.add(
            SyllableToken(
                surface = word[pos].toString(),
                tokenClass = tokenClass,
                isGlide = isGlide,
                startIdx = pos,
                endIdx = pos + 1,
            ),
        )

        pos++
    }

    companion object {
        private const val COMBINING_DIAERESIS = '̈'

        private fun isNonspacingMark(ch: Char): Boolean = Character.getType(ch) == Character.NON_SPACING_MARK.toInt()
    }
}

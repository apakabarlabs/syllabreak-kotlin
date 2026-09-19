package fm.apakabar.syllabreak

class Tokenizer(private val rule: LanguageRule) {
    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val wordPattern = Regex("[\\p{L}\\p{M}]+")

        var lastEnd = 0
        wordPattern.findAll(text).forEach { match ->
            if (match.range.first > lastEnd) {
                tokens.add(
                    Token(
                        text = text.substring(lastEnd, match.range.first),
                        type = TokenType.NON_WORD,
                    ),
                )
            }

            tokens.add(
                Token(
                    text = match.value,
                    type = TokenType.WORD,
                ),
            )

            lastEnd = match.range.last + 1
        }

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
    rawWord: String,
    private val rule: LanguageRule,
) {
    private val word = recomposeCategoryFlips(rawWord, rule)
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
        for (p in endPos until word.length) {
            val ch = wordLower[p]
            if (!isNonspacingMark(ch)) return false
            if (ch == COMBINING_DIAERESIS) return true
        }
        return false
    }

    private fun addSingleCharacterToken() {
        val char = wordLower[pos]
        val tokenClass = classifyLetter(char, rule) ?: TokenClass.OTHER
        tokens.add(
            SyllableToken(
                surface = word[pos].toString(),
                tokenClass = tokenClass,
                startIdx = pos,
                endIdx = pos + 1,
            ),
        )
        pos++
    }

    companion object {
        private const val COMBINING_DIAERESIS = '̈'

        private fun isNonspacingMark(ch: Char): Boolean = Character.getType(ch) == Character.NON_SPACING_MARK.toInt()

        private fun classifyLetter(
            char: Char,
            rule: LanguageRule,
        ): TokenClass? =
            when {
                char in rule.vowels -> TokenClass.VOWEL
                char in rule.consonants -> TokenClass.CONSONANT
                else -> null
            }

        private fun recomposeCategoryFlips(
            word: String,
            rule: LanguageRule,
        ): String {
            val sb = StringBuilder()
            var i = 0
            while (i < word.length) {
                var end = i + 1
                while (end < word.length && isNonspacingMark(word[end])) end++
                if (end > i + 1) {
                    val composed = java.text.Normalizer.normalize(word.substring(i, end), java.text.Normalizer.Form.NFC)
                    if (composed.length == 1) {
                        val baseClass = classifyLetter(word[i].lowercaseChar(), rule)
                        val composedClass = classifyLetter(composed[0].lowercaseChar(), rule)
                        if (composedClass != null && composedClass != baseClass) {
                            sb.append(composed)
                            i = end
                            continue
                        }
                    }
                }
                sb.append(word[i])
                i++
            }
            return sb.toString()
        }
    }
}

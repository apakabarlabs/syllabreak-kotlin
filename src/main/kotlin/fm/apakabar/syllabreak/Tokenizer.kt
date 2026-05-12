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
        if (char !in rule.modifiersAttachLeft) return false

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

    private fun tryMatchConsonantDigraph(): Boolean {
        // Length 3 supports trigraphs like Hungarian "dzs" and German "sch".
        for (length in listOf(3, 2, 1)) {
            if (pos + length > word.length) continue

            val substr = wordLower.substring(pos, pos + length)
            if (substr in rule.dontSplitDigraphs) {
                tokens.add(
                    SyllableToken(
                        surface = word.substring(pos, pos + length),
                        tokenClass = TokenClass.CONSONANT,
                        startIdx = pos,
                        endIdx = pos + length,
                    ),
                )
                pos += length
                return true
            }
        }
        return false
    }

    private fun tryMatchVowelDigraph(): Boolean {
        for (length in listOf(3, 2)) {
            if (pos + length > word.length) continue

            val substr = wordLower.substring(pos, pos + length)
            if (substr in rule.digraphVowels) {
                val isGlide = substr.any { it in rule.glides }
                tokens.add(
                    SyllableToken(
                        surface = word.substring(pos, pos + length),
                        tokenClass = TokenClass.VOWEL,
                        isGlide = isGlide,
                        startIdx = pos,
                        endIdx = pos + length,
                    ),
                )
                pos += length
                return true
            }
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

        // Handle right-attaching modifiers
        if (pos + 1 < word.length && wordLower[pos + 1] in rule.modifiersAttachRight) {
            tokens.last().apply {
                surface += word[pos + 1]
                endIdx = pos + 2
                isModifier = true
            }
            pos++
        }

        pos++
    }
}

package fm.apakabar.syllabreak

enum class TokenType {
    WORD,
    NON_WORD,
}

enum class TokenClass {
    VOWEL,
    CONSONANT,
    SEPARATOR,
    OTHER,
}

data class Token(
    val text: String,
    val type: TokenType,
)

data class SyllableToken(
    var surface: String,
    val tokenClass: TokenClass,
    var isModifier: Boolean = false,
    val startIdx: Int = 0,
    var endIdx: Int = 0,
)

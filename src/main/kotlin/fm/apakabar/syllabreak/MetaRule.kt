package fm.apakabar.syllabreak

class MetaRule(initialRules: List<LanguageRule>) {
    val rules: List<LanguageRule> =
        initialRules.map { rule ->
            val uniqueChars =
                rule.allChars.toMutableSet().apply {
                    for (otherRule in initialRules) {
                        if (otherRule.lang != rule.lang) {
                            removeAll(otherRule.allChars)
                        }
                    }
                }
            rule.copy(uniqueChars = uniqueChars, meta = this)
        }

    fun getAllKnownChars(): Set<Char> {
        return rules.flatMap { it.allChars }.toSet()
    }

    fun findMatches(text: String): List<LanguageRule> {
        if (text.isEmpty()) return emptyList()

        val cleanText = text.lowercase().filter { it.isLetter() }
        if (cleanText.isEmpty()) return emptyList()

        val matches = mutableListOf<Pair<LanguageRule, Double>>()

        for (rule in rules) {
            var score = rule.calculateMatchScore(text)
            if (score > 0) {
                if (rule.uniqueChars.isNotEmpty() && cleanText.any { it in rule.uniqueChars }) {
                    score = 1.0
                }
                matches.add(rule to score)
            }
        }

        return matches.sortedByDescending { it.second }.map { it.first }
    }
}

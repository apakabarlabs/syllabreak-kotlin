package fm.apakabar.syllabreak

class WordSyllabifier(private val rule: LanguageRule) {
    fun syllabifyWord(
        word: String,
        softHyphen: String,
    ): String {
        val syllabifier = WordSyllabification(word, rule, softHyphen)
        return syllabifier.syllabify()
    }
}

private class WordSyllabification(
    originalWord: String,
    private val rule: LanguageRule,
    private val softHyphen: String,
) {
    private val originalWord: String = originalWord
    private val expanded: Pair<String, List<LanguageRule.GeminateSpan>> = rule.expandGeminateDigraphs(originalWord)
    private val word: String = expanded.first
    private val geminateSpans: List<LanguageRule.GeminateSpan> = expanded.second
    private val tokens: List<SyllableToken> = tokenize()
    private val nuclei: List<Int> = findNuclei()

    private fun tokenize(): List<SyllableToken> {
        val tokenizer = SyllableTokenizer(word, rule)
        return tokenizer.tokenize()
    }

    private fun findNuclei(): List<Int> {
        val nuclei = mutableListOf<Int>()

        // First pass: find vowels
        tokens.forEachIndexed { index, token ->
            if (token.tokenClass == TokenClass.VOWEL) {
                nuclei.add(index)
            }
        }

        // Check for final semivowels (e.g., Romanian final -i after consonant)
        // These don't form a separate syllable nucleus
        if (nuclei.isNotEmpty() && rule.finalSemivowels.isNotEmpty()) {
            val lastNucleusIdx = nuclei.last()
            val lastToken = tokens[lastNucleusIdx]

            // Check if it's the last token (or only followed by non-letters)
            val isFinal =
                ((lastNucleusIdx + 1) until tokens.size).all { j ->
                    val tokenClass = tokens[j].tokenClass
                    tokenClass == TokenClass.SEPARATOR || tokenClass == TokenClass.OTHER
                }

            if (isFinal) {
                // Check if final vowel is in semivowels set
                val firstChar = lastToken.surface.lowercase().firstOrNull()
                if (firstChar != null && firstChar in rule.finalSemivowels) {
                    // Check if preceded by consonant
                    if (lastNucleusIdx > 0) {
                        val prevIdx = lastNucleusIdx - 1
                        if (tokens[prevIdx].tokenClass == TokenClass.CONSONANT) {
                            // Remove this nucleus - it's a semivowel, not a syllable
                            nuclei.removeAt(nuclei.lastIndex)
                        }
                    }
                }
            }
        }

        // Check for syllabic consonants surrounded by other consonants
        // (e.g., Serbian "r" in "prljav" -> "pr-ljav")
        // Must have consonant on both sides AND have at least one consonant
        // between it and the nearest vowel on BOTH sides
        if (rule.syllabicConsonants.isNotEmpty() && nuclei.isNotEmpty()) {
            val syllabicNuclei = mutableListOf<Int>()
            tokens.forEachIndexed { i, token ->
                if (token.tokenClass != TokenClass.CONSONANT) return@forEachIndexed
                val char = token.surface.lowercase().firstOrNull() ?: return@forEachIndexed
                if (char !in rule.syllabicConsonants) return@forEachIndexed

                // Check if surrounded by consonants (not adjacent to vowels)
                val prevIsConsonant = (i == 0) || (tokens[i - 1].tokenClass == TokenClass.CONSONANT)
                val nextIsConsonant = (i == tokens.size - 1) || (tokens[i + 1].tokenClass == TokenClass.CONSONANT)
                if (!prevIsConsonant || !nextIsConsonant) return@forEachIndexed

                // Find distance to nearest vowel before (or word start)
                var distToPrevVowel = i + 1 // default: distance to word start
                for (j in (i - 1) downTo 0) {
                    if (tokens[j].tokenClass == TokenClass.VOWEL) {
                        distToPrevVowel = i - j
                        break
                    }
                }

                // Find distance to nearest vowel after (or word end)
                var distToNextVowel = tokens.size - i // default: distance to word end
                for (j in (i + 1) until tokens.size) {
                    if (tokens[j].tokenClass == TokenClass.VOWEL) {
                        distToNextVowel = j - i
                        break
                    }
                }

                // Syllabic consonant only if there's at least one consonant between
                // it and nearest vowel on BOTH sides (distance > 1)
                val hasBufferBefore = distToPrevVowel > 1
                val hasBufferAfter = distToNextVowel > 1
                if (hasBufferBefore && hasBufferAfter) {
                    syllabicNuclei.add(i)
                }
            }

            // Merge syllabic consonant nuclei with vowel nuclei
            if (syllabicNuclei.isNotEmpty()) {
                nuclei.addAll(syllabicNuclei)
                nuclei.sort()
            }
        }

        if (nuclei.isNotEmpty()) {
            return nuclei
        }

        // Fallback: if no vowels at all, try syllabic consonants anywhere
        tokens.forEachIndexed { index, token ->
            if (token.tokenClass == TokenClass.CONSONANT &&
                token.surface.lowercase().firstOrNull() in rule.syllabicConsonants
            ) {
                nuclei.add(index)
            }
        }

        return nuclei
    }

    private fun skipSeparatorsForward(start: Int): Int {
        var pos = start
        while (pos < tokens.size && tokens[pos].tokenClass == TokenClass.SEPARATOR) {
            pos++
        }
        return pos
    }

    private fun skipSeparatorsBackward(start: Int): Int {
        var pos = start
        while (pos >= 0 && tokens[pos].tokenClass == TokenClass.SEPARATOR) {
            pos--
        }
        return pos
    }

    private fun extractConsonantCluster(
        left: Int,
        right: Int,
    ): Pair<List<SyllableToken>, List<Int>> {
        val cluster = mutableListOf<SyllableToken>()
        val clusterIndices = mutableListOf<Int>()

        for (i in left..right) {
            if (i < tokens.size && tokens[i].tokenClass == TokenClass.CONSONANT) {
                cluster.add(tokens[i])
                clusterIndices.add(i)
            }
        }

        return cluster to clusterIndices
    }

    private fun findClusterBetweenNuclei(
        nk: Int,
        nk1: Int,
    ): Pair<List<SyllableToken>, List<Int>> {
        val left = skipSeparatorsForward(nk + 1)
        val right = skipSeparatorsBackward(nk1 - 1)
        return extractConsonantCluster(left, right)
    }

    private fun isValidOnset(
        consonant1: String,
        consonant2: String,
        prevNucleusIdx: Int? = null,
    ): Boolean {
        val onsetCandidate = consonant1.lowercase() + consonant2.lowercase()

        // Check if this cluster requires a long vowel before it
        if (onsetCandidate in rule.clustersOnlyAfterLong && prevNucleusIdx != null) {
            if (!isLongNucleus(prevNucleusIdx)) {
                return false
            }
        }

        return onsetCandidate in rule.clustersKeepNext
    }

    private fun isLongNucleus(nucleusIdx: Int): Boolean {
        if (nucleusIdx >= tokens.size) return false

        val vowelToken = tokens[nucleusIdx]

        // Check if this vowel token itself is already a digraph
        if (vowelToken.surface.lowercase() in rule.digraphVowels) {
            return true
        }

        // Check if current vowel + next character forms a digraph vowel
        if (nucleusIdx + 1 < tokens.size) {
            val nextToken = tokens[nucleusIdx + 1]
            val digraph = vowelToken.surface.lowercase() + nextToken.surface.lowercase()
            if (digraph in rule.digraphVowels) {
                return true
            }
        }

        return false
    }

    /**
     * V-CV: boundary before single consonant.
     *
     * Exception: Don't split V-r-e patterns (care, here, more) when:
     * - At word end, OR
     * - Before light suffixes (-s, -less, -ful, -ly, -ing, -ed)
     *
     * But split AFTER the consonant when followed by breaking suffixes (-ent, -ence, -ency, -ment):
     * - parent -> par-ent, adherent -> ad-her-ent
     */
    private fun findBoundaryForSingleConsonant(
        clusterIndices: List<Int>,
        nk: Int,
        nk1: Int,
    ): Int? {
        val consonantIdx = clusterIndices[0]

        // Check for protected sequences (like -are, -ere, -ore, -ure, -ire)
        if (rule.finalSequencesKeep.isNotEmpty()) {
            // Build the sequence from current vowel nucleus through next nucleus
            val sequence = tokens.subList(nk, nk1 + 1).joinToString("") { it.surface.lowercase() }

            if (sequence in rule.finalSequencesKeep) {
                // Get the rest of the word starting from next nucleus (includes the vowel)
                val restWithVowel = tokens.subList(nk1, tokens.size).joinToString("") { it.surface.lowercase() }
                val restAfterVowel =
                    if (nk1 + 1 < tokens.size) {
                        tokens.subList(nk1 + 1, tokens.size).joinToString("") { it.surface.lowercase() }
                    } else {
                        ""
                    }

                // Check if followed by a breaking suffix (par-ent, ad-her-ent)
                // The suffix starts from the next vowel: "ent" in "par-ent"
                if (rule.suffixesBreakVre.isNotEmpty()) {
                    for (suffix in rule.suffixesBreakVre) {
                        if (restWithVowel == suffix || restWithVowel.startsWith(suffix)) {
                            // Split after consonant = before next nucleus
                            return nk1
                        }
                    }
                }

                // Check if at word end or followed by light suffix (care, care-less)
                val isAtEnd = nk1 == tokens.size - 1
                val hasLightSuffix =
                    rule.suffixesKeepVre.isNotEmpty() &&
                        restAfterVowel.isNotEmpty() &&
                        restAfterVowel in rule.suffixesKeepVre

                if (isAtEnd || hasLightSuffix) {
                    // Don't split - return null to indicate no boundary
                    return null
                }
            }
        }

        return consonantIdx
    }

    private fun findBoundaryInCluster(
        cluster: List<SyllableToken>,
        clusterIndices: List<Int>,
        nk: Int,
        nk1: Int,
    ): Int? {
        return when (cluster.size) {
            0 -> {
                // Check for vowel hiatus (adjacent vowels that form separate syllables)
                if (!rule.splitHiatus) {
                    return null
                }

                // Check if nuclei are adjacent (or only separated by modifiers/separators)
                val areAdjacent =
                    if (nk1 - nk == 1) {
                        true
                    } else {
                        (nk + 1 until nk1).all {
                            tokens[it].tokenClass == TokenClass.SEPARATOR
                        }
                    }

                if (areAdjacent) {
                    // Check if these two vowels form a digraph (don't split)
                    val vowelPair = tokens[nk].surface.lowercase() + tokens[nk1].surface.lowercase()
                    if (vowelPair in rule.digraphVowels) {
                        return null
                    }
                    // Hiatus: split between vowels
                    return nk1
                }
                null
            }
            1 -> findBoundaryForSingleConsonant(clusterIndices, nk, nk1)
            2 -> {
                // Two consonant cluster
                if (isValidOnset(cluster[0].surface, cluster[1].surface, nk)) {
                    clusterIndices[0]
                } else {
                    clusterIndices[1]
                }
            }
            else -> {
                // Long cluster (3+ consonants). Try the last THREE consonants
                // as a single onset first (Greek στρ in ά-στρο); fall back to
                // the 2-letter check.
                if (cluster.size >= 3) {
                    val onset3 =
                        (
                            cluster[cluster.size - 3].surface +
                                cluster[cluster.size - 2].surface +
                                cluster.last().surface
                        ).lowercase()
                    if (onset3 in rule.clustersKeepNext) {
                        return clusterIndices[clusterIndices.size - 3]
                    }
                }
                var boundaryIdx = clusterIndices.last()
                if (cluster.size >= 2 &&
                    isValidOnset(cluster[cluster.size - 2].surface, cluster.last().surface, nk)
                ) {
                    boundaryIdx = clusterIndices[clusterIndices.size - 2]
                }
                boundaryIdx
            }
        }
    }

    private fun placeBoundaries(): List<Int> {
        val boundaries = mutableListOf<Int>()

        for (k in 0 until nuclei.size - 1) {
            val nk = nuclei[k]
            val nk1 = nuclei[k + 1]

            val (cluster, clusterIndices) = findClusterBetweenNuclei(nk, nk1)
            val boundaryIdx = findBoundaryInCluster(cluster, clusterIndices, nk, nk1)

            if (boundaryIdx != null) {
                boundaries.add(boundaryIdx)
            }
        }

        return boundaries
    }

    fun syllabify(): String {
        rule.exceptions[originalWord.lowercase()]?.let { return applyException(it) }

        // When the word doesn't actually split, hand back the original surface
        // so any geminate-digraph expansion isn't visible to the caller.
        if (nuclei.size < 2) {
            return originalWord
        }

        val boundaries = placeBoundaries()
        if (boundaries.isEmpty()) {
            return originalWord
        }

        return renderWithGeminateSpans(boundaries)
    }

    /**
     * Render the result, collapsing geminate expansions that don't split.
     *
     * For each geminate span we keep the expanded surface only when a
     * boundary actually falls between its tokens. Otherwise the span
     * collapses back to its original compact text — the caller never sees
     * a cosmetic expansion that wasn't earned by an actual line break.
     */
    private fun renderWithGeminateSpans(boundaries: List<Int>): String {
        val boundarySet = boundaries.toSet()
        val spanRanges = spanTokenRanges()
        val spansWithInternal = spansContainingAnyBoundary(spanRanges, boundarySet)
        val tokenToSpan = tokenToSpanIndex(spanRanges)

        val output = StringBuilder()
        var i = 0
        while (i < tokens.size) {
            val sIdx = tokenToSpan[i]
            if (sIdx != null && sIdx !in spansWithInternal) {
                val (first, last, compact) = spanRanges[sIdx]
                if (i == first && i in boundarySet) {
                    output.append(softHyphen)
                }
                output.append(compact)
                i = last + 1
            } else {
                if (i in boundarySet) {
                    output.append(softHyphen)
                }
                output.append(tokens[i].surface)
                i++
            }
        }
        return output.toString()
    }

    private data class TokenSpanRange(val first: Int, val last: Int, val compact: String)

    private fun spanTokenRanges(): List<TokenSpanRange> {
        val ranges = mutableListOf<TokenSpanRange>()
        for (span in geminateSpans) {
            val end = span.start + span.length
            var first: Int? = null
            var last: Int? = null
            tokens.forEachIndexed { i, token ->
                if (token.startIdx >= span.start && token.endIdx <= end) {
                    if (first == null) first = i
                    last = i
                }
            }
            if (first != null && last != null) {
                ranges.add(TokenSpanRange(first!!, last!!, span.compactOriginal))
            }
        }
        return ranges
    }

    private fun spansContainingAnyBoundary(
        ranges: List<TokenSpanRange>,
        boundarySet: Set<Int>,
    ): Set<Int> {
        val result = mutableSetOf<Int>()
        ranges.forEachIndexed { sIdx, range ->
            for (b in boundarySet) {
                if (b > range.first && b <= range.last) {
                    result.add(sIdx)
                    break
                }
            }
        }
        return result
    }

    private fun tokenToSpanIndex(ranges: List<TokenSpanRange>): Map<Int, Int> {
        val mapping = mutableMapOf<Int, Int>()
        ranges.forEachIndexed { sIdx, range ->
            for (t in range.first..range.last) {
                mapping[t] = sIdx
            }
        }
        return mapping
    }

    private fun applyException(splitLower: String): String {
        val result = StringBuilder()
        var srcIdx = 0
        for (ch in splitLower) {
            if (ch == '-') {
                result.append(softHyphen)
            } else {
                result.append(originalWord[srcIdx])
                srcIdx++
            }
        }
        return result.toString()
    }
}

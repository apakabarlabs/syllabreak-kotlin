package fm.apakabar.syllabreak

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RulesYaml(
    val rules: List<RuleYaml>,
)

@Serializable
data class RuleYaml(
    val lang: String,
    val vowels: String,
    val consonants: String,
    @SerialName("clusters_keep_next")
    val clustersKeepNext: List<String>? = null,
    @SerialName("trailing_onsets")
    val trailingOnsets: List<String>? = null,
    @SerialName("dont_split_digraphs")
    val dontSplitDigraphs: List<String>? = null,
    @SerialName("digraph_vowels")
    val digraphVowels: List<String>? = null,
    @SerialName("vowel_glides")
    val vowelGlides: String? = null,
    @SerialName("syllabic_consonants")
    val syllabicConsonants: String? = null,
    @SerialName("modifiers_attach_left")
    val modifiersAttachLeft: String? = null,
    @SerialName("modifiers_separators")
    val modifiersSeparators: String? = null,
    @SerialName("clusters_only_after_long")
    val clustersOnlyAfterLong: List<String>? = null,
    @SerialName("split_hiatus")
    val splitHiatus: Boolean? = null,
    @SerialName("final_semivowels")
    val finalSemivowels: String? = null,
    @SerialName("final_sequences_keep")
    val finalSequencesKeep: List<String>? = null,
    @SerialName("suffixes_break_vre")
    val suffixesBreakVre: List<String>? = null,
    @SerialName("suffixes_keep_vre")
    val suffixesKeepVre: List<String>? = null,
    val exceptions: Map<String, String>? = null,
    @SerialName("geminate_digraphs")
    val geminateDigraphs: Map<String, String>? = null,
)

package fm.apakabar.syllabreak

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportedLanguagesTests {
    @Test
    fun `returns known language codes`() {
        val langs = Syllabreak().supportedLanguages()
        // Spot-check across alphabetic families so future additions don't
        // silently break the list, while leaving room for new languages.
        listOf("eng", "rus", "srp-cyrl", "srp-latn", "bos", "hrv", "cnr-latn", "cnr-cyrl").forEach { code ->
            assertTrue(code in langs, "expected $code in supportedLanguages()")
        }
    }

    @Test
    fun `returns a stable non-empty list`() {
        val s = Syllabreak()
        val first = s.supportedLanguages()
        assertTrue(first.isNotEmpty())
        assertEquals(first, s.supportedLanguages())
    }
}

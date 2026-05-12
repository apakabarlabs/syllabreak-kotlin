[![Tests](https://github.com/apakabarlabs/syllabreak-kotlin/actions/workflows/tests.yml/badge.svg)](https://github.com/apakabarlabs/syllabreak-kotlin/actions/workflows/tests.yml)
# syllabreak-kotlin

Multilingual library for accurate and deterministic hyphenation and syllable counting without relying on dictionaries.

This is a Kotlin/JVM port of [syllabreak-python](https://github.com/apakabarlabs/syllabreak-python). Rules and tests are synced from there via `make sync-yaml`.

## Supported Languages

- 🇬🇧 English (`eng`)
- 🇷🇺 Russian (`rus`)
- 🇷🇸 Serbian Cyrillic (`srp-cyrl`)
- 🇷🇸 Serbian Latin (`srp-latn`)
- 🇧🇦 Bosnian (`bos`)
- 🇭🇷 Croatian (`hrv`)
- 🇲🇪 Montenegrin Latin (`cnr-latn`)
- 🇲🇪 Montenegrin Cyrillic (`cnr-cyrl`)
- 🇹🇷 Turkish (`tur`)
- 🇬🇪 Georgian (`kat`)
- 🇩🇪 German (`deu`)
- 🇫🇷 French (`fra`)
- 🇷🇴 Romanian (`ron`)
- 🇪🇸 Spanish (`spa`)
- 🇵🇹 Portuguese (`por`)
- 🇵🇱 Polish (`pol`)
- 🏛️ Latin (`lat`)

## Why syllabification isn't trivial

A few language-specific quirks the algorithm has to encode. Each one would otherwise produce visibly wrong splits.

- **BCMS (bos, hrv, cnr)** — long-jat reflex `ije` is **one** syllable: `mli-je-ko` is wrong, `mlije-ko` is correct. Two graphic-but-not-jat exceptions are `dvije` and `prije` (Matešić 2015, rule P11). `srp-latn` does not encode `ije` because Serbian dictionaries cover both ekavian and ijekavian; pass `lang="hrv"` (or `bos`/`cnr-latn`) for ijekavian text.
- **Montenegrin** adds `ś`/`ź` (Latin) and `с́`/`з́` (Cyrillic, decomposed `с` + U+0301 only — no precomposed Unicode points exist).
- **French** — `eau` is a trigraph vowel: `châ-teau`.
- **Romanian** — final `-i` after a consonant is palatalization, not a separate syllable.
- **German** — `st` between vowels splits after a short nucleus but stays together after a long one.
- **Latin** — hiatus is mandatory.
- **Polish** — digraphs `sz`, `cz`, `rz`, `dz`, `ch` stay together.
- **BCMS** — syllabic `r` between consonants is a syllable nucleus: `prst` and `krv` are one syllable.
- **Georgian** — no digraphs; consonant sequences split unless on a small whitelist of valid onsets.

For BCMS specifically, character-based auto-detect cannot tell `bos`/`hrv`/`srp-latn`/`cnr-latn` apart for text without script-unique letters — the detector returns `srp-latn` first to preserve prior behaviour. Pass `lang=` explicitly to get ijekavian handling.

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("fm.apakabar:syllabreak-kotlin:0.5.1")
}
```

## Usage

### Auto-detect language

When no language is specified, the library automatically detects the most likely language:

```kotlin
import fm.apakabar.syllabreak.Syllabreak

val s = Syllabreak("-")
println(s.syllabify("hello"))        // "hel-lo"
println(s.syllabify("здраво"))       // "здра-во" (Serbian Cyrillic)
println(s.syllabify("привет"))       // "при-вет" (Russian)
```

### Specify language explicitly

You can specify the language code for more predictable results:

```kotlin
val s = Syllabreak("-")
println(s.syllabify("problem", "eng"))      // "pro-blem" (Force English rules)
println(s.syllabify("problem", "srp-latn")) // "prob-lem" (Force Serbian Latin rules)
println(s.syllabify("mlijeko", "hrv"))      // "mlije-ko" (Croatian ije is one syllable)
```

This is useful when:
- The text could match multiple languages
- You want consistent rules for a specific language
- Processing text in a known language

### Language detection

You can detect languages that match the input text:

```kotlin
val s = Syllabreak()
println(s.detectLanguage("hello"))   // ["eng"]
println(s.detectLanguage("здраво"))  // ["srp-cyrl"]
println(s.detectLanguage("привет"))  // ["rus"]
```

### Custom soft hyphen

By default, the library uses the Unicode soft hyphen (`­`), but you can customize it:

```kotlin
val s = Syllabreak("|")  // Use pipe as separator
println(s.syllabify("syllabification"))  // "syl|la|bi|fi|ca|tion"
```

## Out of Scope

Some writing systems do not fit syllabreak's alphabetic-rules paradigm and will not be added — they need fundamentally different algorithms:

- **Chinese, Japanese, Korean** — logographic / mora-syllabic / Hangul-block-based; no vowel/consonant rule engine applies.
- **Arabic** — abjad; short vowels are optional diacritics, so syllabification is undecidable without vocalization.
- **Bengali, Hindi, Sanskrit** — Brahmic abugidas; the unit is the akṣara, which requires Unicode grapheme-cluster logic rather than a flat character table.

## Lines of Code

<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/loc-history-dark.svg">
  <source media="(prefers-color-scheme: light)" srcset=".github/loc-history-light.svg">
  <img alt="Lines of Code graph" src=".github/loc-history-light.svg">
</picture>

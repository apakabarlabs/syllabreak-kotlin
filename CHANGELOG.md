# Changelog

## 0.20.1

### Changed

- Published to Maven Central as `fm.apakabar:syllabreak-kotlin`, signed. Until
  now the library was taken as a source checkout wired into the consumer's build.
  The code is the same as 0.20.0.

  Before, in `settings.gradle.kts` and the module's `build.gradle.kts`:

  ```kotlin
  include(":syllabreak")
  project(":syllabreak").projectDir = file("../syllabreak-kotlin")

  dependencies { implementation(project(":syllabreak")) }
  ```

  After, with `mavenCentral()` among the repositories:

  ```kotlin
  dependencies { implementation("fm.apakabar:syllabreak-kotlin:0.20.1") }
  ```

## 0.20.0

### Fixed

- English final `e` and `ed` use ordered nucleus rules for regular silent
  endings, syllabic `-ed`, consonant + `le`, `-isle`, `-gue`, and `-que`, with
  a bounded lexical layer for non-productive pronunciations.
- Polish vowel hiatus and `cj` boundaries now follow the shared rule corpus,
  with lexical overrides for loanword diphthongs.

## 0.19.0

### Fixed — syllable-division correctness
- **`й` no longer counted as a syllable nucleus** (rus/kaz/kir). It decomposes
  under NFD to `и` + combining breve; the engine now recomposes it back to the
  consonant before tokenisation, so `мой` → `мой`, `война` → `вой-на`.
- **Russian vowel hiatus splits**: `по-э-зи-я`, `на-у-ка`, `со-юз`.
- **Hard sign `ъ` holds the syllable boundary**: `об-ъект`, `под-ъезд`, `из-ъян`.
- **Kazakh/Kyrgyz `у`/`и`**: Kazakh models them as context-dependent glides
  (`да-уа`, not `да-у-а`; `ди-а-лог`); Kyrgyz splits hiatus (`а-ян`, `кы-ял`).

### Changed — internal, behaviour-preserving
- Removed dead rule fields `modifiers_attach_right`, `sonorants`, `glides`
  (and the `isGlide` token flag), and the redundant empty-default lines in
  `rules.yaml`.
- Deduplicated the BCMS-Latin and Serbian/Montenegrin Cyrillic rule families
  with YAML anchors.
- **YAML loading moved from Jackson to kotaml** (a maintained kaml fork on
  kotlinx.serialization) so that YAML anchors resolve; Jackson does not expand
  them. Kotlin bumped to 2.3.20.

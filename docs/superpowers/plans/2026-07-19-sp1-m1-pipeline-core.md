# Zbor SP1 · Milestone 1 — Pipeline Core & Edit Seam · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the pure-Kotlin conversation-turn state machine whose defining feature is the *edit seam* — translation and TTS never run until the speaker confirms (and may correct) the transcribed text.

**Architecture:** A framework-free `:core` Gradle module holds the pipeline. `PipelineOrchestrator` drives one turn through explicit states, calling three engine interfaces (`SttEngine`, `MtEngine`, `TtsEngine`) it receives by constructor injection. Milestone 1 exercises the whole flow with fake engines on the JVM — no Android, no native libs, no models. Real engines (M2), UI (M3), and delivery (M4) plug into these interfaces later without changing this module.

**Tech Stack:** Kotlin (JVM), Gradle (Kotlin DSL), JUnit 5 + kotlin.test. Android SDK is NOT a dependency of `:core`.

## Global Constraints

- **Platform floor:** app targets Android; the user's device is a Redmi Note 15 Pro 5G. `:core` itself stays pure-Kotlin/JVM (no `android.*` imports) so it is unit-testable without an emulator.
- **Languages:** exactly four — Macedonian (`MK`), Brazilian Portuguese (`PT`), English (`EN`), German (`DE`).
- **Edit seam is mandatory and inviolable:** `MtEngine` MUST NOT be invoked before the input text is confirmed; `TtsEngine` MUST synthesize the *confirmed* text (post-edit), never the raw STT output.
- **TTS backend for SP1:** espeak-ng (robotic, offline) behind `TtsEngine`; the trained mk voice (SP2) swaps in behind the same interface. M1 uses a fake.
- **Commit discipline:** commit at every step boundary that says Commit; small commits, not one big one (matches Allan's existing app discipline).
- **Repo:** `/home/allan/projects/zbor - macedonian conversation app ` (trailing space) (already `git init`-ed; branch `main`).

---

## File Structure (Milestone 1)

- `settings.gradle.kts` — declares the `:core` module.
- `build.gradle.kts` (root) — Kotlin JVM + JUnit 5 plugin/config.
- `core/build.gradle.kts` — `:core` module build (kotlin-jvm, test deps).
- `core/src/main/kotlin/mk/zbor/core/Lang.kt` — `Lang` enum + `Utterance` type.
- `core/src/main/kotlin/mk/zbor/core/Engines.kt` — `SttEngine`, `MtEngine`, `TtsEngine` interfaces + `EngineException`.
- `core/src/main/kotlin/mk/zbor/core/TurnState.kt` — sealed `TurnState` hierarchy + `Stage` enum.
- `core/src/main/kotlin/mk/zbor/core/PipelineOrchestrator.kt` — the state machine.
- `core/src/test/kotlin/mk/zbor/core/Fakes.kt` — fake/spy engines shared by tests.
- `core/src/test/kotlin/mk/zbor/core/PipelineOrchestratorTest.kt` — behavior tests.

One responsibility per file; the orchestrator is the only stateful class.

---

## Task 1: Module skeleton + domain types

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `core/build.gradle.kts`
- Create: `core/src/main/kotlin/mk/zbor/core/Lang.kt`
- Test: `core/src/test/kotlin/mk/zbor/core/LangTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class Lang { MK, PT, EN, DE }`; `data class Utterance(val pcm: ShortArray, val sampleRate: Int)`.

- [ ] **Step 1: Write the failing test**

`core/src/test/kotlin/mk/zbor/core/LangTest.kt`:
```kotlin
package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LangTest {
    @Test fun `four supported languages`() {
        assertEquals(4, Lang.entries.size)
    }
    @Test fun `utterance keeps pcm and rate`() {
        val u = Utterance(shortArrayOf(1, 2, 3), 16_000)
        assertEquals(16_000, u.sampleRate)
        assertEquals(3, u.pcm.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test`
Expected: FAIL — `Lang` / `Utterance` unresolved (compilation error).

- [ ] **Step 3: Write build files and domain types**

`settings.gradle.kts`:
```kotlin
rootProject.name = "zbor"
include(":core")
```

`build.gradle.kts` (root):
```kotlin
plugins { kotlin("jvm") version "2.0.21" apply false }
```

`core/build.gradle.kts`:
```kotlin
plugins { kotlin("jvm") version "2.0.21" }
repositories { mavenCentral() }
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
tasks.test { useJUnitPlatform() }
```

`core/src/main/kotlin/mk/zbor/core/Lang.kt`:
```kotlin
package mk.zbor.core

/** The four languages Zbor handles. */
enum class Lang { MK, PT, EN, DE }

/** A chunk of mono 16-bit PCM audio. */
data class Utterance(val pcm: ShortArray, val sampleRate: Int) {
    override fun equals(other: Any?) =
        other is Utterance && sampleRate == other.sampleRate && pcm.contentEquals(other.pcm)
    override fun hashCode() = 31 * pcm.contentHashCode() + sampleRate
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts core/
git commit -m "feat(core): module skeleton, Lang and Utterance types"
```

---

## Task 2: Engine interfaces

**Files:**
- Create: `core/src/main/kotlin/mk/zbor/core/Engines.kt`
- Test: `core/src/test/kotlin/mk/zbor/core/Fakes.kt` (shared fakes; first used here)

**Interfaces:**
- Consumes: `Lang`, `Utterance`.
- Produces:
  - `interface SttEngine { fun transcribe(audio: Utterance, lang: Lang): String }`
  - `interface MtEngine { fun translate(text: String, from: Lang, to: Lang): String }`
  - `interface TtsEngine { fun synthesize(text: String, lang: Lang): Utterance }`
  - `class EngineException(val stage: Stage, message: String) : Exception(message)` — note `Stage` is defined in Task 3; if implementing Task 2 alone, add a minimal `enum class Stage { STT, MT, TTS }` now and Task 3 will reuse it.

- [ ] **Step 1: Write the failing test (fakes must satisfy the interfaces)**

`core/src/test/kotlin/mk/zbor/core/Fakes.kt`:
```kotlin
package mk.zbor.core

/** Returns a canned transcript; records calls. */
class FakeStt(private val out: String) : SttEngine {
    var calls = 0; private set
    override fun transcribe(audio: Utterance, lang: Lang): String { calls++; return out }
}

/** Echoes "<to>:<text>"; records the exact text it was asked to translate. */
class FakeMt : MtEngine {
    var calls = 0; private set
    var lastText: String? = null; private set
    override fun translate(text: String, from: Lang, to: Lang): String {
        calls++; lastText = text; return "$to:$text"
    }
}

/** Produces a 1-sample utterance; records the exact text it was asked to speak. */
class FakeTts : TtsEngine {
    var calls = 0; private set
    var lastText: String? = null; private set
    override fun synthesize(text: String, lang: Lang): Utterance {
        calls++; lastText = text; return Utterance(shortArrayOf(text.length.toShort()), 16_000)
    }
}
```

`core/src/test/kotlin/mk/zbor/core/EnginesTest.kt`:
```kotlin
package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals

class EnginesTest {
    @Test fun `fakes conform to interfaces`() {
        val stt: SttEngine = FakeStt("здраво")
        val mt: MtEngine = FakeMt()
        val tts: TtsEngine = FakeTts()
        assertEquals("здраво", stt.transcribe(Utterance(shortArrayOf(0), 16_000), Lang.MK))
        assertEquals("PT:здраво", mt.translate("здраво", Lang.MK, Lang.PT))
        assertEquals(1, tts.synthesize("oi", Lang.PT).pcm.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test`
Expected: FAIL — `SttEngine`/`MtEngine`/`TtsEngine`/`Stage` unresolved.

- [ ] **Step 3: Write the interfaces**

`core/src/main/kotlin/mk/zbor/core/Engines.kt`:
```kotlin
package mk.zbor.core

/** Which pipeline stage an engine failure came from. */
enum class Stage { STT, MT, TTS }

/** Speech -> text for one language. */
interface SttEngine { fun transcribe(audio: Utterance, lang: Lang): String }

/** Text -> text across a language pair. */
interface MtEngine { fun translate(text: String, from: Lang, to: Lang): String }

/** Text -> speech for one language. */
interface TtsEngine { fun synthesize(text: String, lang: Lang): Utterance }

/** Thrown by an engine; carries the stage so the orchestrator can report it. */
class EngineException(val stage: Stage, message: String) : Exception(message)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/mk/zbor/core/Engines.kt core/src/test/kotlin/mk/zbor/core/Fakes.kt core/src/test/kotlin/mk/zbor/core/EnginesTest.kt
git commit -m "feat(core): STT/MT/TTS engine interfaces + test fakes"
```

---

## Task 3: Turn state model

**Files:**
- Create: `core/src/main/kotlin/mk/zbor/core/TurnState.kt`
- Test: `core/src/test/kotlin/mk/zbor/core/TurnStateTest.kt`

**Interfaces:**
- Consumes: `Lang`, `Utterance`, `Stage`.
- Produces sealed `TurnState`:
  - `Idle`
  - `Recording(from: Lang, to: Lang)`
  - `AwaitingInputConfirm(sttText: String, from: Lang, to: Lang)`
  - `AwaitingOutputConfirm(mtText: String, to: Lang)`
  - `Done(sttText: String, mtText: String, audio: Utterance)`
  - `Failed(stage: Stage, reason: String)`

- [ ] **Step 1: Write the failing test**

`core/src/test/kotlin/mk/zbor/core/TurnStateTest.kt`:
```kotlin
package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TurnStateTest {
    @Test fun `states carry their data`() {
        val a = TurnState.AwaitingInputConfirm("здраво", Lang.MK, Lang.PT)
        assertEquals(Lang.PT, a.to)
        val f = TurnState.Failed(Stage.STT, "empty")
        assertEquals(Stage.STT, f.stage)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test`
Expected: FAIL — `TurnState` unresolved.

- [ ] **Step 3: Write the sealed hierarchy**

`core/src/main/kotlin/mk/zbor/core/TurnState.kt`:
```kotlin
package mk.zbor.core

/** Every observable state of a single conversation turn. */
sealed interface TurnState {
    data object Idle : TurnState
    data class Recording(val from: Lang, val to: Lang) : TurnState
    data class AwaitingInputConfirm(val sttText: String, val from: Lang, val to: Lang) : TurnState
    data class AwaitingOutputConfirm(val mtText: String, val to: Lang) : TurnState
    data class Done(val sttText: String, val mtText: String, val audio: Utterance) : TurnState
    data class Failed(val stage: Stage, val reason: String) : TurnState
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/mk/zbor/core/TurnState.kt core/src/test/kotlin/mk/zbor/core/TurnStateTest.kt
git commit -m "feat(core): TurnState sealed model"
```

---

## Task 4: Orchestrator — record → STT → edit seam (the core invariant)

**Files:**
- Create: `core/src/main/kotlin/mk/zbor/core/PipelineOrchestrator.kt`
- Modify: `core/src/test/kotlin/mk/zbor/core/PipelineOrchestratorTest.kt` (create)

**Interfaces:**
- Consumes: engines, `TurnState`.
- Produces:
  - `class PipelineOrchestrator(stt, mt, tts, outputSeam: Boolean = false)`
  - `val state: TurnState` (current)
  - `fun startTurn(from: Lang, to: Lang): TurnState` → `Recording`
  - `fun submitAudio(audio: Utterance): TurnState` → runs STT → `AwaitingInputConfirm` (or `Failed(STT)` if STT throws; stays `AwaitingInputConfirm` with empty text if transcript is blank — never advances past the seam on garbage).

- [ ] **Step 1: Write the failing test — MT must NOT run before confirm**

`core/src/test/kotlin/mk/zbor/core/PipelineOrchestratorTest.kt`:
```kotlin
package mk.zbor.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun audio() = Utterance(shortArrayOf(0, 0, 0), 16_000)

class PipelineOrchestratorTest {
    @Test fun `submitAudio transcribes and halts at input seam without translating`() {
        val mt = FakeMt()
        val o = PipelineOrchestrator(FakeStt("здраво"), mt, FakeTts())
        o.startTurn(Lang.MK, Lang.PT)
        val s = o.submitAudio(audio())
        assertIs<TurnState.AwaitingInputConfirm>(s)
        assertEquals("здраво", s.sttText)
        assertEquals(0, mt.calls) // seam holds: no translation yet
    }

    @Test fun `stt failure yields Failed(STT)`() {
        val stt = object : SttEngine {
            override fun transcribe(audio: Utterance, lang: Lang) =
                throw EngineException(Stage.STT, "boom")
        }
        val o = PipelineOrchestrator(stt, FakeMt(), FakeTts())
        o.startTurn(Lang.MK, Lang.PT)
        val s = o.submitAudio(audio())
        assertIs<TurnState.Failed>(s)
        assertEquals(Stage.STT, s.stage)
    }

    @Test fun `blank transcript stays at seam for manual entry`() {
        val o = PipelineOrchestrator(FakeStt("   "), FakeMt(), FakeTts())
        o.startTurn(Lang.MK, Lang.PT)
        val s = o.submitAudio(audio())
        assertIs<TurnState.AwaitingInputConfirm>(s)
        assertTrue(s.sttText.isBlank())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test`
Expected: FAIL — `PipelineOrchestrator` unresolved.

- [ ] **Step 3: Implement start/submit half of the orchestrator**

`core/src/main/kotlin/mk/zbor/core/PipelineOrchestrator.kt`:
```kotlin
package mk.zbor.core

/**
 * Drives one conversation turn. The edit seam is enforced structurally:
 * [submitAudio] can only reach [TurnState.AwaitingInputConfirm]; translation
 * happens exclusively in [confirmInput] (Task 5).
 */
class PipelineOrchestrator(
    private val stt: SttEngine,
    private val mt: MtEngine,
    private val tts: TtsEngine,
    private val outputSeam: Boolean = false,
) {
    var state: TurnState = TurnState.Idle
        private set

    fun startTurn(from: Lang, to: Lang): TurnState {
        state = TurnState.Recording(from, to)
        return state
    }

    fun submitAudio(audio: Utterance): TurnState {
        val rec = state as? TurnState.Recording
            ?: error("submitAudio called in state $state; expected Recording")
        state = try {
            val text = stt.transcribe(audio, rec.from)
            TurnState.AwaitingInputConfirm(text, rec.from, rec.to)
        } catch (e: EngineException) {
            TurnState.Failed(e.stage, e.message ?: "STT failed")
        }
        return state
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/mk/zbor/core/PipelineOrchestrator.kt core/src/test/kotlin/mk/zbor/core/PipelineOrchestratorTest.kt
git commit -m "feat(core): orchestrator record->STT->input seam, MT held back"
```

---

## Task 5: Orchestrator — confirmInput → MT → (optional output seam) → TTS → Done

**Files:**
- Modify: `core/src/main/kotlin/mk/zbor/core/PipelineOrchestrator.kt`
- Modify: `core/src/test/kotlin/mk/zbor/core/PipelineOrchestratorTest.kt`

**Interfaces:**
- Consumes: state from Task 4.
- Produces:
  - `fun confirmInput(editedText: String): TurnState` — from `AwaitingInputConfirm`: translate `editedText` (NOT the raw STT text); then if `outputSeam` → `AwaitingOutputConfirm`, else synthesize and → `Done`. `Failed(MT)`/`Failed(TTS)` on engine error.
  - `fun confirmOutput(editedText: String): TurnState` — from `AwaitingOutputConfirm`: synthesize `editedText` → `Done`.

- [ ] **Step 1: Write the failing tests — edited text propagates; seam routing**

Append to `PipelineOrchestratorTest.kt`:
```kotlin
class PipelineConfirmTest {
    @Test fun `confirmInput translates the EDITED text, then speaks it`() {
        val mt = FakeMt(); val tts = FakeTts()
        val o = PipelineOrchestrator(FakeStt("здравo-typo"), mt, tts) // outputSeam=false
        o.startTurn(Lang.MK, Lang.PT)
        o.submitAudio(Utterance(shortArrayOf(0), 16_000))
        val s = o.confirmInput("здраво") // user corrected the transcript
        assertIs<TurnState.Done>(s)
        assertEquals("здраво", mt.lastText)   // edited text was translated
        assertEquals("PT:здраво", tts.lastText) // confirmed translation was spoken
        assertEquals("PT:здраво", s.mtText)
    }

    @Test fun `output seam halts before TTS until confirmOutput`() {
        val tts = FakeTts()
        val o = PipelineOrchestrator(FakeStt("здраво"), FakeMt(), tts, outputSeam = true)
        o.startTurn(Lang.MK, Lang.PT)
        o.submitAudio(Utterance(shortArrayOf(0), 16_000))
        val mid = o.confirmInput("здраво")
        assertIs<TurnState.AwaitingOutputConfirm>(mid)
        assertEquals(0, tts.calls)            // not spoken yet
        val done = o.confirmOutput("PT:здраво editado")
        assertIs<TurnState.Done>(done)
        assertEquals("PT:здраво editado", tts.lastText)
    }

    @Test fun `mt failure yields Failed(MT)`() {
        val mt = object : MtEngine {
            override fun translate(text: String, from: Lang, to: Lang) =
                throw EngineException(Stage.MT, "nope")
        }
        val o = PipelineOrchestrator(FakeStt("здраво"), mt, FakeTts())
        o.startTurn(Lang.MK, Lang.PT)
        o.submitAudio(Utterance(shortArrayOf(0), 16_000))
        val s = o.confirmInput("здраво")
        assertIs<TurnState.Failed>(s)
        assertEquals(Stage.MT, s.stage)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test`
Expected: FAIL — `confirmInput`/`confirmOutput` unresolved.

- [ ] **Step 3: Implement the confirm half**

Add to `PipelineOrchestrator` (inside the class):
```kotlin
    fun confirmInput(editedText: String): TurnState {
        val seam = state as? TurnState.AwaitingInputConfirm
            ?: error("confirmInput called in state $state; expected AwaitingInputConfirm")
        state = try {
            val mtText = mt.translate(editedText, seam.from, seam.to)
            if (outputSeam) {
                TurnState.AwaitingOutputConfirm(mtText, seam.to)
            } else {
                TurnState.Done(editedText, mtText, tts.synthesize(mtText, seam.to))
            }
        } catch (e: EngineException) {
            TurnState.Failed(e.stage, e.message ?: "${e.stage} failed")
        }
        return state
    }

    fun confirmOutput(editedText: String): TurnState {
        val seam = state as? TurnState.AwaitingOutputConfirm
            ?: error("confirmOutput called in state $state; expected AwaitingOutputConfirm")
        state = try {
            TurnState.Done(editedText, editedText, tts.synthesize(editedText, seam.to))
        } catch (e: EngineException) {
            TurnState.Failed(e.stage, e.message ?: "TTS failed")
        }
        return state
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test`
Expected: PASS (all classes).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/mk/zbor/core/PipelineOrchestrator.kt core/src/test/kotlin/mk/zbor/core/PipelineOrchestratorTest.kt
git commit -m "feat(core): confirmInput/confirmOutput — MT+TTS on confirmed text"
```

---

## Task 6: Full-turn integration test (both directions, both seam modes)

**Files:**
- Modify: `core/src/test/kotlin/mk/zbor/core/PipelineOrchestratorTest.kt`

**Interfaces:**
- Consumes: the finished orchestrator. Produces: no new production code — this task is the milestone's acceptance gate.

- [ ] **Step 1: Write the failing integration test**

Append:
```kotlin
class PipelineIntegrationTest {
    @Test fun `mk to pt full turn, no output seam`() {
        val o = PipelineOrchestrator(FakeStt("здраво"), FakeMt(), FakeTts())
        assertIs<TurnState.Recording>(o.startTurn(Lang.MK, Lang.PT))
        assertIs<TurnState.AwaitingInputConfirm>(o.submitAudio(Utterance(shortArrayOf(1), 16_000)))
        val done = o.confirmInput("здраво")
        assertIs<TurnState.Done>(done)
        assertEquals("PT:здраво", done.mtText)
    }

    @Test fun `pt to mk full turn, with output seam`() {
        val o = PipelineOrchestrator(FakeStt("oi"), FakeMt(), FakeTts(), outputSeam = true)
        o.startTurn(Lang.PT, Lang.MK)
        o.submitAudio(Utterance(shortArrayOf(1), 16_000))
        assertIs<TurnState.AwaitingOutputConfirm>(o.confirmInput("oi"))
        assertIs<TurnState.Done>(o.confirmOutput("здраво"))
    }
}
```

- [ ] **Step 2: Run test to verify it passes (code already exists)**

Run: `./gradlew :core:test`
Expected: PASS — all tests green. If any fail, fix the orchestrator, not the test.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/kotlin/mk/zbor/core/PipelineOrchestratorTest.kt
git commit -m "test(core): full-turn integration, both directions and seam modes"
```

---

## Milestone 1 Done-Definition

`./gradlew :core:test` green, covering: seam holds MT back, edited text is what gets translated and spoken, optional output seam, blank-transcript handling, and STT/MT/TTS failure surfacing. This is the whole conversational contract, provable without a phone.

---

## SP1 Roadmap (Milestones 2–4 — to be detailed as their own plans when reached)

- **M2 — Real engines + ModelMgr (Android module `:app` + native).** `WhisperSttEngine` (whisper.cpp via JNI), `NllbMtEngine` (ONNX Runtime, int8, NNAPI), `EspeakTtsEngine` (espeak-ng JNI) + per-language MMS voices for pt/en/de, and `ModelMgr` with the HOT/WARM/COLD residency policy. Each implements the M1 interfaces; golden-file instrumented tests on the Redmi. Big unknowns live here (JNI builds, NNAPI) — plan after M1 proves the contract.
- **M3 — UI (`:app`).** Split-screen (top half rotated 180°), icon-driven tap-to-talk, waveform recording state, editable text field wired to the edit seam, ▶ replay, flag-grid language pickers. Drives `PipelineOrchestrator`.
- **M4 — Model delivery.** First-run wifi download of the ~0.9GB pack from **GitHub Releases** (user `allan-valin`), checksum verify, resumable; manual sideload import fallback; corrupt/missing-pack block screen.

SP2 (train the Macedonian voice) is a separate spec→plan cycle; it swaps a `TtsEngine` implementation behind this same M1 interface.

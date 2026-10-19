# Zbor — offline Macedonian conversation translator

**Zbor** (Macedonian *збор*, "word") is an Android app for talking in person with
someone who speaks only Macedonian, with **no internet connection**. It translates
**Macedonian ↔ Portuguese (Brazil), English, German**, and it **speaks the
translation out loud**, including Macedonian (Google Translate no longer speaks
Macedonian).

How a turn works:

1. Tap the microphone and speak (or tap the keyboard button and type).
2. The app writes down what it heard. **Correct it if it misheard**, then tap ✓.
3. The translation appears on the other half of the screen and is spoken aloud.
   🔊 replays it.

Out of the box every language uses the robotic eSpeak NG voice. In ⚙ → **Natural
voices** you can download a human-sounding voice per language (each 60–115 MB):
Macedonian (Meta's Bulgarian MMS voice reading Macedonian, so with a Bulgarian accent),
Brazilian Portuguese, English and German (Piper voices). *Manage voices…* deletes them
one by one. **Feedback from
Macedonian speakers is exactly what this test is for** — see *What to test* below.

> **Status: test version (0.4).** Android only, 64-bit ARM phones (practically every
> phone from 2018 on). Needs about **1.4 GB of free space** and a **one-time
> 1.2 GB download over Wi-Fi**; after that it works completely offline.
> Updating from 0.1: install the new APK over the old one, open it and tap
> **Download** once more — only the new voice (108 MB) is fetched.

---

## Contents

- [For testers: install on your phone (no computer needed)](#for-testers-install-on-your-phone-no-computer-needed)
- [Using the app](#using-the-app)
- [What to test and how to report it](#what-to-test-and-how-to-report-it)
- [For the person (or AI assistant) helping a tester](#for-the-person-or-ai-assistant-helping-a-tester)
- [Developer install from a computer (terminal commands)](#developer-install-from-a-computer-terminal-commands)
- [Building from source](#building-from-source)

---

## For testers: install on your phone (no computer needed)

### Downloads

| What | Link |
|---|---|
| The app (`zbor-0.4.1.apk`, ~50 MB) | https://github.com/allan-valin/zbor/releases/latest |
| The offline model pack (7 files, 1.2 GB) | the app downloads it by itself on first start — nothing to do |

### Step 1 — allow your phone to install the app

Zbor is not in the Play Store, so Android will ask whether it may install an app
from your browser or file manager. This is normal for test apps.

- Video (general Android, ~2 min): [How to Install APK Files on Android](https://www.youtube.com/watch?v=FHYBaJnI9sU)
- Alternative video: [How to Install APK Files on your Android Phone (Best Method)](https://www.youtube.com/watch?v=YSjDltPlKv8)

In words:

1. Open the download link above **on the phone** and download `zbor-0.4.1.apk`.
2. Open the downloaded file (from the browser's download notification, or the
   **Files / Downloads** app).
3. Android says the browser is not allowed to install unknown apps → tap
   **Settings** → turn on **Allow from this source** → go back.
   - Path if you need to find it yourself: *Settings → Apps → Special app access →
     Install unknown apps → (your browser or Files app) → Allow*.
   - **Xiaomi / Redmi / POCO (HyperOS/MIUI):** the phone may run a "security scan"
     and warn about an unknown app. Choose **Install anyway** / **Continue**. If
     installing is blocked completely, turn off *Settings → Additional settings →
     Developer options → "MIUI/HyperOS optimization"* only if you know what that
     is — otherwise ask the person who sent you the app.
   - **Samsung:** *Settings → Security and privacy → Install unknown apps*.
4. Tap **Install**, then **Open**.

You can switch *Allow from this source* off again afterwards.

### Step 2 — first start: download the models (once, on Wi-Fi)

The first screen is the setup screen. The app's menus follow your phone's
language: **English**, **German** or **Portuguese** (any other phone language,
including Macedonian, gets English).

| English | Deutsch | Português |
|---|---|---|
| **Download** ← tap this | **Herunterladen** | **Baixar** |
| Download anyway (mobile data) | Trotzdem herunterladen (mobile Daten) | Baixar mesmo assim (rede móvel) |
| Pause | Pause | Pausar |
| Import from a folder… | Aus einem Ordner importieren… | Importar de uma pasta… |
| Check the files again | Dateien erneut prüfen | Conferir os arquivos de novo |
| ok / missing / damaged | ok / fehlt / beschädigt | ok / falta / corrompido |

1. Connect to **Wi-Fi**, tap **Download**.
2. Keep the app open until it finishes (1.2 GB — a few minutes on fast Wi-Fi). The
   screen stays on by itself. If the connection drops, tap **Download** again: it
   **continues where it stopped**.
3. When it is done, the conversation screen opens. From now on no internet is needed.

### Step 3 — allow the microphone

The first time you tap the microphone, Android asks for permission → **Allow**
(*While using the app*).

---

## Using the app

The screen has two halves, one per language. The top half is **Macedonian 🇲🇰**,
the bottom half is your phone's language (🇧🇷 / 🇩🇪 / 🇬🇧) — tap the language
button on either half to choose another (🇲🇰 🇧🇷 🇬🇧 🇩🇪).

**Normal mode** (default): both halves upright. You speak or type on the
**bottom** half; the translation appears on the top half. **⇅** swaps the two
languages (e.g. to let the Macedonian speaker use the bottom controls).

**Face-to-face mode** (*Face to face* / *Gegenüber* / *Frente a frente* in the
middle bar): the top half turns upside down and gets its own microphone, for a
conversation across a table — each person uses their own half.

| Button | What it does |
|---|---|
| 🎤 (big yellow) | Tap, speak, tap again to stop |
| ⌨ | Type instead of speaking |
| ✓ (turns green) | "The text is right — translate and speak it" |
| 🔊 | Speak the last translation again |
| ⇅ | Swap the languages (normal mode) |
| 🕘 | **History**: every translation so far (selectable text, ▶ to hear it again) |
| ✕ | While typing: cancel and go back to what was on screen |
| ⚙ | Settings: **natural voices** per language (▶ example, download, on/off; *Manage voices…* to delete), **voice speed** (slider + 🔊 *play example*; normal is 175 words/min) and *review the translation before speaking* |

Each half shows its hints in its own language, so the Macedonian speaker only
ever needs to read Macedonian.

**Keyboard:** when you type on a half, Zbor asks the keyboard for that half's
language (Cyrillic for Macedonian). The keyboard can only switch to layouts it
has, so add Macedonian once: in **Gboard** → ⚙ *Settings → Languages → Add
keyboard → Macedonian (Македонски)*; on Samsung Keyboard → *Languages and types
→ Manage input languages*. Without it, switch layouts by hand (🌐 or swipe the
space bar).

---

## What to test and how to report it

We especially need people who speak Macedonian.

**Easiest: the rating sheet.** Download `zbor-rating-v2.html` from the
[latest release](https://github.com/allan-valin/zbor/releases/latest) and open it in any
browser (phone or computer, no internet needed after download). It plays 41 sentences
in three anonymous voices; rate each from 1 to 5, then tap *Copy results* and send
them back. About 20 minutes.

In the app, useful things to try:

1. **The Macedonian voice.** Type or say a few everyday sentences in Portuguese,
   English or German and let the app speak the Macedonian. *Can you understand
   it? Which words sound wrong?* Compare with the robotic voice (⚙ → natural
   Macedonian voice off).
2. **Recognition.** Speak Macedonian into the microphone. *How often does the
   written text match what you said? Which words does it get wrong?* (Correcting
   them before ✓ is part of the design.)
3. **Translation.** *Are the translations correct and natural?* Idioms are
   expected to come out word-for-word — note them anyway.
4. **The Macedonian hints** on the top half (e.g. "Притиснете го микрофонот или
   пишувајте"). They were not written by a native speaker. *Are they correct?
   What would you say instead?*
5. **Voice speed.** In ⚙, try the speed slider with *play example*. *Which speed
   is easiest to understand?* Tell us the number.
6. **Speed of the app.** How many seconds from ✓ to hearing the voice?

Please send: your phone model, the sentence you used, what you expected, what
happened (a screenshot helps), to the person who sent you the app — or open an
issue at https://github.com/allan-valin/zbor/issues.

---

## For the person (or AI assistant) helping a tester

This section is written so a helper — a friend, or an AI assistant the tester
pastes this README into — can walk a non-technical tester through installation.
Go one step at a time and confirm each before moving on.

1. **Check the phone.** Ask for: phone brand/model, Android version (*Settings →
   About phone*), free storage (needs ~1.2 GB). The app needs a 64-bit ARM phone
   (any phone sold since ~2018). It does **not** run on iPhone.
2. **Download the APK on the phone** from the releases link above. If the tester
   downloaded it on a computer instead, it can be sent to the phone by e-mail,
   messaging app or USB cable and opened from there.
3. **Unknown-apps permission.** When the tester opens the APK, Android shows a
   prompt to allow the source app (browser, Files, WhatsApp, …). Have them tap
   *Settings* in that prompt and enable *Allow from this source*. Brand-specific
   paths are under Step 1 above. On Xiaomi, a "security scan" warning is
   expected; *Install anyway*.
4. **"App not installed" errors:**
   - *Package conflicts / signatures don't match*: an older test build is
     installed — uninstall Zbor first (long-press the icon → *Uninstall*), then
     install again. Uninstalling also deletes the downloaded models.
   - *Not compatible*: the phone is 32-bit or not ARM — it cannot run this build.
   - *Blocked by Play Protect*: tap *More details → Install anyway*.
5. **First start.** Make sure the phone is on **Wi-Fi**, then tap **Download**
   (*Herunterladen* / *Baixar*; the menus follow the phone language, see the
   table in Step 2). If the button says *Download anyway (mobile data)*, they are
   on mobile data — stop and switch to Wi-Fi unless they have a large data plan.
6. **If the download fails** (message starting with *Download interrupted*): tap
   *Download* again; it resumes. If a file shows *damaged*, *Download* replaces
   just that file.
7. **Microphone.** On the first 🎤 tap, choose *Allow*. If the tester denied it,
   the status line reads "Нема дозвола за микрофон" / "Sem permissão para o
   microfone": fix in *Settings → Apps → Zbor → Permissions → Microphone → Allow*.
8. **Quick check that everything works:** set the bottom half to Português if
   needed, tap ⌨, type `Obrigado pela ajuda`, tap ✓ — the top half should show
   `Ви благодарам за вашата помош.` and the phone should speak it (English:
   `Thank you for your help`). Then hand the
   tester the "What to test" list above.

Offline alternative (no Wi-Fi at the tester's place): someone downloads the five
model files once on a computer (links in the next section), copies them to the
phone (USB cable or SD card) into any folder (e.g. `Download/zbor-pack`), and the
tester uses **Import from a folder…**, opens that folder and taps *Use this
folder* → *Allow*. Import takes well under a minute.

---

## Developer install from a computer (terminal commands)

For people comfortable with a terminal. Uses `adb` (Android Debug Bridge) over a
USB cable.

### 1. Install adb

- **Linux (Debian/Ubuntu):** `sudo apt install adb`
- **macOS:** `brew install android-platform-tools`
- **Windows:** download *SDK Platform-Tools for Windows* from
  https://developer.android.com/tools/releases/platform-tools, unzip it, and run
  the commands below from inside that folder (use `.\adb` instead of `adb`).

### 2. Enable USB debugging on the phone

- Video: [Unlock Developer Options & Enable USB Debugging](https://www.youtube.com/watch?v=7X37nVCYS8U)
  · alternative: [Enable USB Debugging on Android – Step-by-Step](https://www.youtube.com/watch?v=_9ip-fgHtk0)
- In words: *Settings → About phone* → tap **Build number** 7 times (on
  Xiaomi/Redmi: tap **OS version** / *HyperOS version* until it says you are a
  developer) → back to *Settings → (System / Additional settings) → Developer
  options* → turn on **USB debugging**.
- **Xiaomi/Redmi/POCO only:** also turn on **Install via USB** in Developer
  options (needs a Xiaomi account sign-in). During each `adb install` the phone
  may show a prompt — tap **Install** within a few seconds, or the install fails
  with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`.
- Plug in the cable; on the phone accept **Allow USB debugging?** (tick *Always
  allow from this computer*).

Official reference: https://developer.android.com/tools/adb

### 3. Install the app and the model pack

Linux / macOS:

```bash
adb devices                       # the phone must be listed as "device", not "unauthorized"

# the app
curl -L -o zbor.apk https://github.com/allan-valin/zbor/releases/latest/download/zbor-0.4.1.apk
adb install -r zbor.apk           # Xiaomi: tap "Install" on the phone now

# the model pack (1.2 GB) into the phone's Download/zbor-pack folder
R=https://github.com/allan-valin/zbor/releases/download
DEST=/sdcard/Download/zbor-pack
adb shell mkdir -p $DEST
for f in pack-v1/espeak-ng-data.zip pack-v1/ggml-small-q5_1.bin pack-v1/nllb-encoder.ort \
         pack-v1/nllb-decoder.ort pack-v1/nllb-tokenizer.bin pack-v2/mk-voice.onnx pack-v2/mk-voice.vocab.json; do
  curl -L -C - -o "$(basename $f)" "$R/$f"
  adb push "$(basename $f)" "$DEST/"
done

adb shell am start -n mk.zbor/.app.MainActivity
# then on the phone: Import from a folder… → Download → zbor-pack → Use this folder → Allow
```

> Do **not** push into `Android/data/mk.zbor/…`: on Android 16 (tested on a Redmi
> Note 15 Pro) files that adb puts there are invisible to the app, which then
> reports every file as *missing*. Use Download + Import.

Windows (PowerShell, inside the platform-tools folder):

```powershell
.\adb devices
curl.exe -L -o zbor.apk https://github.com/allan-valin/zbor/releases/latest/download/zbor-0.4.1.apk
.\adb install -r zbor.apk
$R = "https://github.com/allan-valin/zbor/releases/download"
$DEST = "/sdcard/Download/zbor-pack"
.\adb shell mkdir -p $DEST
foreach ($f in "pack-v1/espeak-ng-data.zip","pack-v1/ggml-small-q5_1.bin","pack-v1/nllb-encoder.ort","pack-v1/nllb-decoder.ort","pack-v1/nllb-tokenizer.bin","pack-v2/mk-voice.onnx","pack-v2/mk-voice.vocab.json") {
  $n = Split-Path $f -Leaf
  curl.exe -L -C - -o $n "$R/$f"
  .\adb push $n "$DEST/"
}
.\adb shell am start -n mk.zbor/.app.MainActivity
# then on the phone: Import from a folder… → Download → zbor-pack → Use this folder → Allow
```

The import checks every file against its expected size and SHA-256, unpacks the
voice data and opens the conversation screen (about 20 seconds on a Redmi Note 15
Pro). If a file is reported *damaged*, push it again and import again. The copy in
`Download/zbor-pack` can be deleted afterwards.

Useful extras:

```bash
adb logcat -s ZborTest AndroidRuntime     # logs, e.g. when sending a crash report
adb uninstall mk.zbor                     # remove app AND models
```

Wireless instead of USB (Android 11+): *Developer options → Wireless debugging →
Pair device with pairing code*, then `adb pair <ip>:<port>` (enter the code) and
`adb connect <ip>:<port>`; everything else is the same.

---

## Building from source

See [`docs/BUILD.md`](docs/BUILD.md): toolchain (JDK 17+, Android SDK 36, NDK 29,
CMake 3.22.1), native sources (whisper.cpp, espeak-ng), how the model pack is
built and published, and how to run the tests. Design and plans live in
[`docs/superpowers/`](docs/superpowers/).

## Credits and licences

- Speech recognition: [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT) with OpenAI Whisper *small*.
- Translation: Meta's [NLLB-200 distilled 600M](https://huggingface.co/facebook/nllb-200-distilled-600M)
  (weights **CC-BY-NC-4.0** — non-commercial use only), run with
  [ONNX Runtime](https://onnxruntime.ai) (MIT).
- Voices: Meta's [MMS-TTS Bulgarian](https://huggingface.co/facebook/mms-tts-bul) (**CC-BY-NC-4.0**) reading
  Macedonian, and [eSpeak NG](https://github.com/espeak-ng/espeak-ng) (GPL-3.0).
- Voice research data: [FLEURS](https://huggingface.co/datasets/google/fleurs) Macedonian (CC-BY-4.0).
  How the voice was chosen: [`docs/V2-PROGRESS.md`](docs/V2-PROGRESS.md).

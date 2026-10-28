# STRICT ZERO-COST & LOCAL-FIRST TECH STACK

**Project:** Personal AI — Android Voice Memory & Meeting Assistant  
**Policy:** ₹0 ongoing cost. No subscriptions, no paid APIs, no credit-based AI services, no mandatory purchases, and no expiring trial dependencies.  
**Offline Guarantee:** 100% of core audio recording, transcription, meeting minutes generation, daily summary synthesis, local search, and personal AI chat operate completely on-device without internet access after setup.

---

## 1. Operating System & Platform Frameworks

| Component | Repository / Provider | License | Free Forever? | Requires Account / API Key? | Execution Mode | Footprint & RAM | Notes / Redistribution |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Android SDK (API 34/Android 14, minSdk 26)** | Google AOSP / Android Developer Tools | Apache 2.0 / Android SDK License | Yes, 100% Free | None | Local on device | Standard OS API | AOSP native APIs, no billing or network tracking. |
| **Kotlin Standard Library & Coroutines** | [JetBrains/Kotlin](https://github.com/JetBrains/kotlin) | Apache 2.0 | Yes, 100% Free | None | Local on device | < 2 MB | Standard language runtime. |
| **Jetpack Compose & Material 3** | [AndroidX Compose](https://developer.android.com/jetpack/compose) | Apache 2.0 | Yes, 100% Free | None | Local on device | Bundled in APK | Modern declarative reactive UI framework. |
| **Jetpack Room (SQLite ORM + FTS4/5)** | [AndroidX Room](https://developer.android.com/jetpack/androidx/releases/room) | Apache 2.0 | Yes, 100% Free | None | Local SQLite DB | < 1 MB | Embedded relational DB with full-text search. No cloud sync by default. |
| **Jetpack DataStore Preferences** | [AndroidX DataStore](https://developer.android.com/jetpack/androidx/releases/datastore) | Apache 2.0 | Yes, 100% Free | None | Local on device | < 100 KB | Type-safe asynchronous key-value persistence. |
| **Jetpack WorkManager** | [AndroidX Work](https://developer.android.com/jetpack/androidx/releases/work) | Apache 2.0 | Yes, 100% Free | None | Local OS background scheduler | < 300 KB | Schedules guaranteed offline background tasks (e.g. 8:30 PM daily summary). |
| **Android Foreground Service** | Android Framework Core (`android.app.Service`) | Apache 2.0 / AOSP | Yes, 100% Free | None | Local OS process | Standard OS service | Audio recording persistence across screen locks with persistent notification. |

---

## 2. Audio Capture & Storage

| Component | Repository / Provider | License | Free Forever? | Requires Account / API Key? | Execution Mode | Footprint & RAM | Notes / Redistribution |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Android AudioRecord & MediaRecorder** | Android OS Hardware Abstraction | Apache 2.0 / AOSP | Yes, 100% Free | None | Local microphone input | Direct PCM/WAV/AAC buffer (< 5 MB RAM) | Incremental chunking (15-30 min segments) directly into app-private storage. |
| **App-Private Storage** | Android Scoped Storage (`Context.filesDir`) | AOSP | Yes, 100% Free | None | Local flash storage | Configurable retention | Audio stays in sandboxed directories. Zero remote upload. |

---

## 3. Speech-to-Text (STT) Engines

| Component | Repository / Provider | License | Free Forever? | Requires Account / API Key? | Execution Mode | Model Size & RAM | Linguistic Capabilities |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **whisper.cpp (Primary Engine)** | [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) | MIT License | Yes, 100% Free | None | On-device CPU NEON / NPU inference | **tiny:** ~75 MB (RAM ~120 MB)<br>**base:** ~142 MB (RAM ~220 MB)<br>**small:** ~466 MB (RAM ~600 MB) | English, Hindi (`hi`), Hindi-English code-mixed speech, Indian accents, punctuation, timestamps. |
| **Vosk STT (Lightweight Engine)** | [alphacep/vosk-api](https://github.com/alphacep/vosk-api) | Apache 2.0 | Yes, 100% Free | None | On-device Kaldi acoustic inference | **vosk-model-small-en-in:** ~36 MB (RAM ~80 MB)<br>**vosk-model-small-hi:** ~42 MB (RAM ~90 MB) | Ultra-lightweight fallback for budget phones with < 3 GB RAM. |

---

## 4. On-Device AI Summarization & Meeting Minutes

| Component | Repository / Provider | License | Free Forever? | Requires Account / API Key? | Execution Mode | Footprint & RAM | Notes / Redistribution |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Deterministic NLP Core Engine** | Custom Embedded Kotlin Engine (TextRank + Semantic Pattern Matching) | Open-source (Personal AI) | Yes, 100% Free | None | 100% on-device CPU | < 1 MB storage, < 15 MB RAM | Zero hallucination. Extracts: Agenda, Executive Summary, Decisions, Action Items, Deadlines, Assignees, Pending Questions, Risks & Blockers. |
| **llama.cpp / On-Device Quantized LLM (Optional High-RAM Tier)** | [ggerganov/llama.cpp](https://github.com/ggerganov/llama.cpp) + [Hugging Face Open Models (Qwen2.5-0.5B-Instruct-GGUF / SmolLM2-135M-GGUF)](https://huggingface.co) | Apache 2.0 / MIT | Yes, 100% Free | None | Local CPU / GPU / NPU | **135M:** ~90 MB (RAM ~250 MB)<br>**0.5B:** ~350 MB (RAM ~600 MB) | Activated only on devices with >= 4-6 GB RAM. If disabled or model not present, the deterministic NLP core handles all summarization and chat. |

---

## 5. Document Export & Cryptography

| Component | Repository / Provider | License | Free Forever? | Requires Account / API Key? | Execution Mode | Footprint | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Android Native PDF Canvas (`PdfDocument`)** | Android OS Standard Library | Apache 2.0 / AOSP | Yes, 100% Free | None | Local vector rasterizer | 0 KB extra | Clean multi-page PDF generation for meeting minutes and daily summaries. |
| **Standard File Exporters (TXT, Markdown, JSON, CSV)** | Kotlin Standard Library | Apache 2.0 | Yes, 100% Free | None | Local file I/O | 0 KB extra | Clean, human-readable file formats for universal interoperability. |
| **AndroidX Security Crypto** | [AndroidX Security](https://developer.android.com/jetpack/androidx/releases/security) | Apache 2.0 | Yes, 100% Free | None | Local Android Keystore | < 150 KB | Hardware-backed AES-256-GCM encryption for backups and exports. |

---

## 6. Strict Compliance Checklist

- [x] **No Paid Cloud APIs:** Zero calls to OpenAI, Anthropic, Google Cloud Speech, Gemini Cloud, or Azure.
- [x] **No Subscriptions / Billing:** Google Play Billing SDK is strictly prohibited and absent from `build.gradle`.
- [x] **No Free-Trial Degradation:** The software retains 100% functionality perpetually.
- [x] **No Hidden Fallbacks:** If on-device models are missing or uninitialized, explicit UI warnings and download prompts are shown; never silent cloud offloading.
- [x] **Model Redistribution & Direct Download:** Official ggml and Vosk model repositories provide public, unrestricted, royalty-free model weights.

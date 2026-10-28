# Personal AI — Android Voice Memory & Meeting Assistant

A native, on-device personal AI assistant for Android built with Kotlin, Jetpack Compose (Material 3), Room SQLite (with FTS4 full-text search), WorkManager, and local AI engines.

**Budget Rule:** **₹0 ongoing cost.** No subscriptions, no paid APIs, no credit-based AI services, no mandatory purchases, and no expiring trial dependencies. Core audio recording, transcription, meeting intelligence, daily summary briefings, local search, and AI chat operate completely offline on your device.

---

## 🚀 Status: All Phases (1 – 7) Completed & Verified

- **Phase 1 (Toolchain & Zero-Cost Architecture):** Completed.
- **Phase 2 (Android Foundation & Reliable Recording):** Completed & Verified.
- **Phase 3 (On-Device Speech-to-Text Transcription):** Completed & Verified.
- **Phase 4 (Meeting Minutes & Daily AI Summaries):** Completed & Verified.
- **Phase 5 (Local Search, Personal Memory & Source-Grounded AI Chat):** Completed & Verified.
- **Phase 6 (Export Engine, Hardware Encrypted Backup & Retention):** Completed & Verified.
- **Phase 7 (Automated Tests, Verification & Delivery):** Completed & Verified.

---

## 🌟 Comprehensive Feature Suite

### 1. Foreground Voice Recording (`AudioRecordingService`)
- Native linear PCM audio recording at **16,000 Hz, 16-bit mono WAV** (standard format for Vosk/Whisper with zero transcoding overhead).
- Runs reliably across screen locks with an Android persistent foreground notification.
- Notification controls: **Pause, Resume, Stop**, and live recording duration timer.
- Automatic crash reconciliation & WAV header repair if the phone unexpectedly powers off during recording.

### 2. Offline Speech-to-Text (`VoskTranscriptionEngine`)
- Bundled native C++ Kaldi engine (`libvosk.so`) for ARM64/ARMv7/x86.
- 100% on-device local execution; zero network transmission during speech recognition.
- Generates timestamped segments (`startOffsetMs`, `endOffsetMs`) with word confidence metrics and sentence-level punctuation.
- In-app speech model catalog with zero-cost models:
  - **Indian English Accent (`vosk-model-small-en-in-0.4`):** ~36 MB download.
  - **Hindi (`vosk-model-small-hi-0.22`):** ~42 MB download.
  - **Universal English (`vosk-model-small-en-us-0.15`):** ~40 MB download.
- Interactive click-to-seek timestamp pills that jump the audio player to the exact second.
- Inline transcript editing with `VERIFIED` status in SQLite.

### 3. Automatic Meeting Minutes & Action Items (`MeetingMinutesExtractor`)
- Deterministic on-device NLP extractor analyzing transcript segments.
- Extracts:
  - Meeting title & objective
  - Detected attendees / participants
  - Extractive executive summary (frequency & significance scoring)
  - Key discussion points with audio timestamps
  - **Decisions Made**
  - **Action Items** with automatic assignee extraction and deadline parsing ("by Monday", "tomorrow", "Rahul will do")
  - Open questions, risks, and blockers
- Automatically inserts detected action items as interactive tasks into the local database.

### 4. Daily Personal AI Summary (`DailySummaryWorker`)
- Background scheduled task running daily at **8:30 PM** via Android **WorkManager**.
- Aggregates today's audio sessions, meeting minutes, completed tasks, and pending action items into an end-of-day executive briefing.
- Generates a local notification and saves the summary directly to your pinned Notes.
- "Generate Today's Summary Now" manual trigger available on the Home and Settings screens.

### 5. Action Items & Task Manager (`TasksScreen`)
- Filter tasks by **All, Pending, or Completed**.
- Checkbox toggle for instant status changes.
- Manual task creation with optional assignee and deadline.
- Export task list directly to **CSV**.

### 6. Personal Memory & Fast Offline Search (`MemoryScreen`)
- **Global Offline Search:** Sub-millisecond full-text search across transcripts (via Room FTS4), meetings, tasks, and notes.
- **Personal Notes:** Create, edit, pin/unpin, and delete thoughts and memos.

### 7. Source-Grounded Local RAG AI Chat (`ChatScreen` & `LocalRagChatEngine`)
- Conversational assistant answering questions strictly from your recorded memory and notes.
- Clickable citation pills linking directly to source recordings (`[Recording #1 @ 01:23]`), meetings, or tasks.
- **Zero Hallucination Guarantee:** If information is absent from your local memory, explicitly states that no matches were found. Never invents deadlines, decisions, or attendees.

### 8. Document Export & Sharing (`ExportManager`)
- **PDF Export:** Clean, formatted PDF documents generated natively via Android `PdfDocument`.
- **Markdown Export:** `.md` files formatted with headings, checklists, and timestamps.
- **CSV & JSON Export:** Universal structured data export for tasks and recordings.
- Integrated Android System Share Sheet (`FileProvider`).

### 9. Hardware-Backed Encrypted Backup & Restore (`BackupManager`)
- Encrypts your entire database, transcripts, meetings, and notes into an **AES-256-GCM** `.enc` archive using PBKDF2 key derivation from your secret passphrase.
- Restore encrypted snapshots with integrity and password verification.

### 10. Data Retention & Privacy Controls (`RetentionManager`)
- Configurable audio retention policies:
  - *Keep Audio Forever*
  - *Delete Audio After 7 Days*
  - *Delete Audio After 30 Days*
  - *Delete Audio Once Transcribed* (frees disk space while preserving full transcripts and minutes).
- **Nuclear Reset:** Double-confirmed "Clear All Data" option for complete user autonomy.

---

## 🧪 Automated Unit Tests

All 8 test suites pass with 100% success:
- `RoomDatabaseDaoTest`: Verifies Room CRUD, cascading deletes, relationship preservation, and FTS/LIKE transcript search.
- `WavWriterTest`: Verifies 44-byte RIFF/WAVE header generation, little-endian chunk offsets, and file finalization.
- `RecordingStateTest`: Verifies state machine transitions and millisecond-to-clock formatting.
- `TranscriptionPipelineTest`: Verifies transcription queue and model management.
- `MeetingMinutesExtractorTest`: Verifies decision, task, assignee, and deadline extraction from transcript segments.
- `DailySummarySynthesizerTest`: Verifies end-of-day summary synthesis and markdown formatting.
- `BackupManagerTest`: Verifies AES-256-GCM encryption, decryption, and wrong-passphrase rejection.
- `MainScreenViewModelTest`: Verifies ViewModel state flow.

Run tests:
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
cd personal-ai-android
.\gradlew.bat testDebugUnitTest
```

---

## 📦 Verified APK Delivery

- **Output APK Path:**
  `personal-ai-android/app/build/outputs/apk/debug/app-debug.apk`
- **File Size:** ~66.61 MB (includes native offline Vosk STT engines for ARM64/ARMv7/x86).
- **Package Name:** `com.personalai.assistant`
- **Target SDK:** 36 (Android 16 Developer Preview / Android 14+ baseline)
- **Minimum SDK:** 26 (Android 8.0 Oreo)

### Installation Instructions via ADB:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r personal-ai-android\app\build\outputs\apk\debug\app-debug.apk
```
*(On Xiaomi/MIUI/HyperOS devices, confirm the "Install via USB" prompt on screen if prompted).*

---

## 📚 Technical Documentation
- [`docs/FREE_STACK.md`](docs/FREE_STACK.md) — Comprehensive ₹0 stack breakdown and license audit.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — MVVM, database schema, audio pipeline, and security design.
- [`docs/PRIVACY.md`](docs/PRIVACY.md) — Privacy manifesto, sandboxed storage, and zero-telemetry policy.

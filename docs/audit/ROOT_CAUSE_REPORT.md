# BATCH 1 — FORENSIC ROOT CAUSE REPORT

**Project:** Personal AI — Android Voice Memory & Meeting Assistant  
**Repository:** `https://github.com/loveneeta/personal-ai-assistant`  
**Audit Timestamp:** October 29, 2026, 15:25 IST  
**Status:** Audit & Baseline Phase (Inspection Only)  

---

## 1. Summary of Confirmed Defects & Severity Matrix

| ID | Component | Severity | Category | Status | Summary |
|---|---|---|---|---|---|
| **RC-01** | `ModelManager.kt` | **CRITICAL** | Security | Confirmed Root Cause | Zip Slip vulnerability: archive extraction does not validate canonical target path. |
| **RC-02** | `BackupManager.kt` | **HIGH** | Data Integrity | Confirmed Root Cause | `restoreEncryptedBackup` restores meetings/tasks/notes, but completely ignores `recordings` and `transcripts`. |
| **RC-03** | `RetentionManager.kt` | **HIGH** | Functional Gap | Confirmed Root Cause | `executeRetentionCleanup()` is dead code; never invoked automatically or periodically. |
| **RC-04** | `AudioRecordingService.kt` | **HIGH** | Runtime Reliability | Confirmed Root Cause | Missing `PowerManager.WakeLock`; background recording subject to CPU suspension during screen-off Doze. |
| **RC-05** | `LocalRagChatEngine.kt` | **HIGH** | AI Retrieval | Confirmed Root Cause | FTS query builder takes only the single first token > 3 chars (e.g. "what"), discarding the actual query. |
| **RC-06** | `TranscriptionQueueManager.kt` | **MEDIUM** | Resource / Coroutines | Confirmed Root Cause | `resumePendingTranscriptions` launches an unbounded persistent `collect` on Room Flow without lifecycle termination. |
| **RC-07** | `AppRepository.kt` | **MEDIUM** | Data Integrity | Confirmed Root Cause | `clearAllData()` deletes from recordings, transcripts, meetings, tasks, notes, but omits `wakeEventDao.clearAll()`. |
| **RC-08** | `MeetingMinutesExtractor.kt` | **MEDIUM** | Logic / Edge Case | Confirmed Root Cause | `getNextDayOfWeek()` returns current day at 18:00 when called on that same day instead of advancing to next week. |
| **RC-09** | `AudioRecordingService.kt` | **MEDIUM** | Lifecycle Race | Confirmed Root Cause | `onDestroy()` launches async `serviceScope.launch(Dispatchers.IO)` for WAV finalization which can be killed immediately. |
| **RC-10** | `ModelManager.kt` | **LOW** | Resource Leak | Confirmed Root Cause | `HttpURLConnection` in `downloadModel()` lacks explicit `disconnect()` in a `finally` block. |
| **RC-11** | `AppRepository.kt` / `WavWriter.kt` | **LOW** | Code Duplication | Confirmed Root Cause | `AppRepository.repairWavHeader()` duplicates `WavWriter.finalizeWavHeader()` byte logic. |

---

## 2. In-Depth Inspection of Target Modules

### 2.1 `AudioRecordingService.kt` & `WavWriter.kt`
- **File:** [`AudioRecordingService.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/recording/AudioRecordingService.kt)
- **Lines:** 84–148, 366–373
- **Severity:** HIGH
- **Defect 1: Missing WakeLock during Background Recording**
  - *Evidence:* While `JarvisAlwaysOnService` properly holds a `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`), `AudioRecordingService` does NOT acquire any `WakeLock`. 
  - *Impact:* On devices with aggressive OEM battery optimization (Xiaomi/MIUI, Samsung OneUI, OnePlus OxygenOS), when a user records a meeting with the screen turned off, the Android kernel puts the application CPU thread into deep sleep. This results in silent audio gaps, frame drops, or total stoppage until the display is turned back on.
  - *Proposed Fix:* Acquire `PowerManager.PARTIAL_WAKE_LOCK` upon `startSession()` and release it in `cleanupAudioResources()`.
- **Defect 2: Asynchronous WAV Header Finalization in `onDestroy()`**
  - *Evidence:*
    ```kotlin
    override fun onDestroy() {
        super.onDestroy()
        if (isRecordingActive.get()) {
            stopSession() // Launches serviceScope.launch(Dispatchers.IO)
        }
        cleanupAudioResources()
    }
    ```
  - *Impact:* When Android destroys the service due to low memory or user swipe-kill, `serviceScope` may be terminated before `WavWriter.finalizeWavHeader` executes on `Dispatchers.IO`, leaving an unfinalized 0-byte header. Although `AppRepository.reconcileIncompleteRecordings()` repairs headers on subsequent launch, the service itself should synchronously flush and update file length before process death.
  - *Proposed Fix:* Run WAV header finalization synchronously during shutdown or flush directly to disk before triggering background database updates.

---

### 2.2 `TranscriptionEngine.kt` & `TranscriptionQueueManager.kt`
- **Files:** [`TranscriptionEngine.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/transcription/TranscriptionEngine.kt), [`TranscriptionQueueManager.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/transcription/TranscriptionQueueManager.kt)
- **Lines:** `TranscriptionQueueManager.kt:46–62`
- **Severity:** MEDIUM
- **Defect: Unbounded Room Flow Collector in `resumePendingTranscriptions`**
  - *Evidence:*
    ```kotlin
    fun resumePendingTranscriptions() {
        scope.launch {
            val allRecordings = repository.getAllRecordingsFlow()
            launch {
                allRecordings.collect { list ->
                    val pending = list.filter { ... }
                    for (rec in pending) {
                        enqueue(rec.id)
                    }
                }
            }
        }
    }
    ```
  - *Impact:* `allRecordings.collect { ... }` runs continuously on the application-wide `scope`. Whenever ANY recording state changes in Room (e.g. status changed to `PROCESSING`, progress update, duration tick, or new recording inserted), Room re-emits the entire table list. The collector fires repeatedly, iterating through all recordings and calling `enqueue()` on each pending item. While `activeJobMap.putIfAbsent()` prevents duplicate execution, spinning a continuous reactive query for what was meant to be a one-shot startup reconciliation causes unnecessary thread wakeups and GC pressure.
  - *Proposed Fix:* Use `repository.getAllRecordings().filter { ... }` or `allRecordings.first()` for one-shot startup recovery, and enqueue newly completed recordings directly from `AudioRecordingService` or explicit user action.

---

### 2.3 `ModelManager.kt`
- **File:** [`ModelManager.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/transcription/models/ModelManager.kt)
- **Lines:** 142–163, 95–122
- **Severity:** CRITICAL
- **Defect 1: Zip Slip Vulnerability (Path Traversal in Unzip)**
  - *Evidence:*
    ```kotlin
    private fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDirectory, entry.name) // UNSANITIZED ENTRY NAME!
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> ... }
                }
                ...
    ```
  - *Impact:* If an archive contains path traversal characters (e.g. `../../lib/payload.so`), `File(targetDirectory, entry.name)` resolves outside `targetDirectory`. An attacker or compromised mirror could overwrite arbitrary application-private files.
  - *Proposed Fix:* Add a path validation check:
    ```kotlin
    val canonicalTarget = targetDirectory.canonicalPath
    val canonicalFile = file.canonicalPath
    if (!canonicalFile.startsWith(canonicalTarget + File.separator)) {
        throw SecurityException("Zip Slip detected: ${entry.name}")
    }
    ```
- **Defect 2: Unclosed `HttpURLConnection`**
  - *Evidence:* In `downloadModel()`, `connection.disconnect()` is never called in a `finally` block, relying solely on garbage collection.
  - *Proposed Fix:* Wrap HTTP stream consumption in `try / finally` with `connection.disconnect()`.

---

### 2.4 `AppRepository.kt` & `AppDatabase.kt`
- **Files:** [`AppRepository.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/data/repository/AppRepository.kt), [`AppDatabase.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/data/local/AppDatabase.kt)
- **Lines:** `AppRepository.kt:279–294`
- **Severity:** MEDIUM
- **Defect 1: Incomplete Nuclear Reset (`clearAllData`)**
  - *Evidence:*
    ```kotlin
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        val recordings = recordingDao.getAllRecordings()
        for (rec in recordings) { ... file.delete() ... }
        transcriptDao.deleteAll()
        taskDao.deleteAll()
        meetingDao.deleteAll()
        noteDao.deleteAll()
        recordingDao.deleteAll()
        // WAKE_EVENTS TABLE IS NEVER WIPED!
    }
    ```
  - *Impact:* When the user selects "Nuclear Reset" / "Clear All Data" in Settings to wipe all personal data, all meetings and notes are deleted, but `wake_events` records (containing timestamps, recognized voice commands, and assistant response summaries) remain untouched in SQLite.
  - *Proposed Fix:* Add `wakeEventDao.clearAll()` inside `clearAllData()`.

---

### 2.5 `BackupManager.kt`
- **File:** [`BackupManager.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/backup/BackupManager.kt)
- **Lines:** 54–84, 198–242
- **Severity:** HIGH
- **Defect: Backup Encryption Includes Recordings & Transcripts, but Restore Omits Them**
  - *Evidence:*
    - In `createEncryptedBackup`:
      `root.put("recordings", recArray)` (lines 54–68)
      `root.put("transcripts", trArray)` (lines 70–84)
    - In `restoreEncryptedBackup`:
      Lines 198–241 restore `meetings`, `tasks`, and `notes`, but **completely ignore `recordings` and `transcripts`**. Neither `root.optJSONArray("recordings")` nor `root.optJSONArray("transcripts")` is ever read or inserted into Room!
  - *Impact:* A user who backs up their assistant and restores on a new device loses 100% of their audio recording metadata and transcript records!
  - *Proposed Fix:* Implement the parsing and insertion loops for `recordings` and `transcripts` in `restoreEncryptedBackup()`.

---

### 2.6 `RetentionManager.kt`
- **File:** [`RetentionManager.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/retention/RetentionManager.kt)
- **Lines:** 35–68
- **Severity:** HIGH
- **Defect: Dead Retention Execution Code**
  - *Evidence:* `executeRetentionCleanup(context, repository)` contains the cleanup logic for 7-day, 30-day, and immediate post-transcription deletion. A codebase-wide grep confirms that `executeRetentionCleanup` is **never referenced or called anywhere in the entire codebase**.
  - *Impact:* Users who configure "Delete Audio After 7 Days" or "Delete Audio Once Transcribed" will find that raw audio files accumulate indefinitely in internal storage, violating user retention expectations and exhausting disk space.
  - *Proposed Fix:* Invoke `executeRetentionCleanup()`:
    1. Inside `DailySummaryWorker` (scheduled daily at 8:30 PM).
    2. At the end of `TranscriptionQueueManager.processRecording()` when policy is `IMMEDIATE_POST_TRANSCRIPT`.
    3. When the user changes policy in `SettingsViewModel`.

---

### 2.7 `LocalRagChatEngine.kt`
- **File:** [`LocalRagChatEngine.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/ai/LocalRagChatEngine.kt)
- **Lines:** 180–188
- **Severity:** HIGH
- **Defect: Truncated Single-Term RAG Search Strategy**
  - *Evidence:*
    ```kotlin
    val cleanTerms = trimmed.replace(Regex("[^a-zA-Z0-9 ]"), " ")
        .split(" ")
        .filter { it.length > 3 }
    val searchWord = cleanTerms.firstOrNull() ?: trimmed

    val matchedTranscripts = repository.searchTranscripts(searchWord).take(5)
    val matchedNotes = repository.searchNotes(searchWord).take(3)
    val matchedMeetings = repository.searchMeetings(searchWord).take(3)
    ```
  - *Impact:* If a user asks *"what was decided about the project deadline?"*, `cleanTerms` is `["what", "decided", "about", "project", "deadline"]`. `cleanTerms.firstOrNull()` selects `"what"`. The engine queries FTS for `"what*"`, pulling irrelevant sentences that happen to contain the question word "what", and discarding "decided", "project", and "deadline"!
  - *Proposed Fix:* Filter out conversational stop words (`what`, `when`, `where`, `which`, `about`, `could`, `would`, `tell`, `show`) and build an FTS OR/AND search expression over the salient substantive keywords (e.g. `"decided* OR project* OR deadline*"`).

---

### 2.8 `MeetingMinutesExtractor.kt`
- **File:** [`MeetingMinutesExtractor.kt`](file:///C:/Users/LOVENEET/.gemini/antigravity/scratch/personal-ai-assistant/app/src/main/kotlin/com/personalai/assistant/ai/MeetingMinutesExtractor.kt)
- **Lines:** 198–212
- **Severity:** MEDIUM
- **Defect: Same-Day Evaluation Bug in `getNextDayOfWeek`**
  - *Evidence:*
    ```kotlin
    private fun getNextDayOfWeek(dayOfWeek: Int): Long {
        val cal = Calendar.getInstance()
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, 18)
        cal.set(Calendar.MINUTE, 0)
        return cal.timeInMillis
    }
    ```
  - *Impact:* If a meeting is held on a Monday afternoon and someone says "action item by Monday", `cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY` is initially true. The `while` loop does not execute, returning *today* at 18:00 (which may already have passed), rather than next week's Monday.
  - *Proposed Fix:* Always advance `cal.add(Calendar.DAY_OF_YEAR, 1)` at least once before checking `cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek`.

---

## 3. Confirmed Root Causes vs Runtime Hypotheses

| ID | Issue | Classification | Justification / Reproduction Path |
|---|---|---|---|
| **RC-01** | Unzip Path Traversal | **CONFIRMED ROOT CAUSE** | Verified directly in `ModelManager.unzip()`: `File(targetDirectory, entry.name)` directly trusts archive entry paths without canonical path checking. |
| **RC-02** | Incomplete Backup Restore | **CONFIRMED ROOT CAUSE** | Verified directly in `BackupManager.restoreEncryptedBackup()`: `recordings` and `transcripts` arrays are never read from root JSON. |
| **RC-03** | Dead Retention Cleanup | **CONFIRMED ROOT CAUSE** | Verified via global symbol reference search: `executeRetentionCleanup()` has 0 callers in the entire project. |
| **RC-04** | Missing WakeLock in AudioRecordingService | **CONFIRMED ROOT CAUSE** | Verified in `AudioRecordingService.kt`: no instance or call to `PowerManager.WakeLock` exists. |
| **RC-05** | Single First-Word RAG Search | **CONFIRMED ROOT CAUSE** | Verified in `LocalRagChatEngine.kt:183`: `cleanTerms.firstOrNull()` explicitly extracts only the first word > 3 characters. |
| **RC-06** | Persistent Room Flow Collector in QueueManager | **CONFIRMED ROOT CAUSE** | Verified in `TranscriptionQueueManager.resumePendingTranscriptions()`: `allRecordings.collect { ... }` runs indefinitely. |
| **RC-07** | Omission of Wake Events in Nuclear Reset | **CONFIRMED ROOT CAUSE** | Verified in `AppRepository.clearAllData()`: `wakeEventDao.clearAll()` is absent. |
| **RC-08** | Meeting Extractor Same-Day Due Date | **CONFIRMED ROOT CAUSE** | Verified in `MeetingMinutesExtractor.getNextDayOfWeek()`: `while` loop terminates on loop entry if called on target day. |
| **HY-01** | OEM Background Recording Dropouts | **RUNTIME HYPOTHESIS** | While the missing `WakeLock` is confirmed in code, exact dropout duration depends on the device OEM's power-management aggressiveness (e.g. Samsung vs Google Pixel). Requires physical hardware verification. |
| **HY-02** | Vosk Acoustic Recognition on Code-Switched Speech | **RUNTIME HYPOTHESIS** | While post-processing in `SentenceReconstructor` is validated via unit tests, actual phonetic word error rate (WER) on rapid Hindi-English code-switching is subject to the specific Kaldi model lexicon installed by the user. |

---

## 4. Proposed Fixes for Subsequent Batches

1. **Security & Data Integrity Batch:**
   - Harden `ModelManager.unzip()` against Zip Slip vulnerabilities.
   - Complete `BackupManager.restoreEncryptedBackup()` to restore `recordings` and `transcripts`.
   - Add `wakeEventDao.clearAll()` to `AppRepository.clearAllData()`.
2. **Audio Reliability & Background Execution Batch:**
   - Add `PowerManager.PARTIAL_WAKE_LOCK` to `AudioRecordingService`.
   - Wire `RetentionManager.executeRetentionCleanup()` into `DailySummaryWorker` and `TranscriptionQueueManager`.
3. **Intelligence & NLP Precision Batch:**
   - Upgrade `LocalRagChatEngine` query builder with multi-term salience extraction and stop-word filtering.
   - Fix `MeetingMinutesExtractor.getNextDayOfWeek()` same-day rollover logic.
   - Convert `TranscriptionQueueManager.resumePendingTranscriptions()` from a continuous flow collector to a one-shot startup query.

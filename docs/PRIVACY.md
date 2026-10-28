# PRIVACY & SECURITY POLICY

**Application:** Personal AI — Android Voice Memory & Meeting Assistant  
**Core Promise:** 100% On-Device, Privacy-First, Zero Cloud Data Transmission, ₹0 Ongoing Cost.

---

## 1. Principles of On-Device Operation

1. **Local Audio Storage:** All microphone recordings are saved exclusively in the application's private, sandboxed internal directory (`context.filesDir/recordings`). No external services, cloud storage buckets, or remote servers have access to or receive this audio.
2. **Local AI & Speech Inference:** Transcription (Whisper/Vosk) and summarization/NLP engines execute entirely within the on-device CPU/NPU runtime. Audio waveforms and text transcripts are never transmitted over the internet.
3. **No Hidden Background Recording:** The application never secretly records. A visible foreground notification with recording status, elapsed time, and direct controls (Pause, Resume, Stop) is permanently shown whenever the microphone is active.
4. **Android Permissions:**
   - `RECORD_AUDIO`: Requested only when starting recording, with clear explanation.
   - `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MICROPHONE`: Required by modern Android to keep recording while the screen is locked or while switching apps, accompanied by a mandatory persistent notification.
   - `POST_NOTIFICATIONS`: Required on Android 13+ to display the active recording control notification.
5. **No Telemetry / No Tracking:** Zero analytics SDKs (e.g. Firebase Analytics, Mixpanel, Google Analytics), zero crash reporting services that transmit user data, zero advertising networks.
6. **Zero Content Logging:** Transcript texts, spoken words, person names, and extracted meeting minutes are never printed to system `Logcat` logs.
7. **Encrypted Backups:** User-initiated database and transcript exports support hardware-backed AES-256-GCM encryption using the Android Keystore.
8. **Permanent Deletion & Retention:** Users have full control to configure automatic data retention (e.g., auto-delete raw audio after 7, 30, or 90 days once transcribed) or manually perform immediate, irrecoverable deletion of recordings, transcripts, and summaries.

---

## 2. Legal Notice & Consent Requirement

> **Notice to User:** Recording private conversations without the explicit consent of all participants may violate local, state, federal, or workplace privacy laws (e.g. two-party/all-party consent jurisdictions). Always inform conversation participants and obtain their affirmative consent before activating recording.

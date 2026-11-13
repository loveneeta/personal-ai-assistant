"""
Unit Test Suite for J.A.R.V.I.S. Windows Desktop Assistant (Phase 4)
Tests microphone lifecycle, VAD, genuine speaker verification, and AI fallback.
"""

import os
import sys
import time
import math
import json
import queue
import tempfile
import urllib.error
import unittest
from unittest.mock import patch, MagicMock

# Add windows directory to sys.path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from jarvis_windows_assistant import (
    AudioCaptureService,
    SpeakerProfileManager,
    AiQueryEngine,
    AiResponse,
    CrossDeviceCoordinator,
    JarvisWindowsAssistant,
    STATE_IDLE,
    STATE_WAKE_LISTENING,
    STATE_WAKE_DETECTED,
    STATE_VERIFYING_SPEAKER,
    STATE_COMMAND_LISTENING,
    STATE_PROCESSING,
    STATE_SPEAKING,
    STATE_PAUSED,
    STATE_STOPPED,
    STATE_ERROR
)

try:
    import numpy as np
except ImportError:
    np = None


def generate_harmonic_pcm(freq: float, sample_rate: int = 16000, duration_s: float = 0.8) -> list:
    """Generates synthetic harmonic speech-like PCM samples."""
    samples = int(sample_rate * duration_s)
    pcm = []
    for i in range(samples):
        t = i / float(sample_rate)
        # Fundamental frequency + second harmonic
        val = int(math.sin(2.0 * math.pi * freq * t) * 12000.0 +
                  math.sin(2.0 * math.pi * (freq * 2) * t) * 6000.0)
        pcm.append(max(-32768, min(32767, val)))
    return pcm


class TestJarvisWindowsAudioCapture(unittest.TestCase):

    def test_audio_capture_initialization_and_shutdown(self):
        service = AudioCaptureService()
        self.assertFalse(service.is_capturing())

        # Start audio capture
        started = service.start()
        if started:
            self.assertTrue(service.is_capturing())
            chunk = service.read_chunk(timeout=0.5)
            # Stop capture
            service.stop()
            self.assertFalse(service.is_capturing())
        else:
            # If no physical microphone attached in test runner, verify graceful error reporting
            self.assertFalse(service.is_capturing())
            self.assertIsNotNone(service.last_error)

    def test_audio_capture_pause_and_resume(self):
        service = AudioCaptureService()
        started = service.start()
        if started:
            self.assertTrue(service.is_capturing())

            # Pause
            service.pause()
            self.assertFalse(service.is_capturing())

            # Resume
            service.resume()
            self.assertTrue(service.is_capturing())

            service.stop()
            self.assertFalse(service.is_capturing())

    def test_audio_capture_bounded_queue_no_unbounded_growth(self):
        service = AudioCaptureService()
        # Mock callback with 150 incoming frames (exceeding MAX_QUEUE_SIZE of 100)
        dummy_chunk = [100] * 1600
        service._is_capturing.set()
        service._is_paused.clear()

        for _ in range(150):
            service._audio_callback(
                indata=np.array(dummy_chunk, dtype='int16') if np is not None else dummy_chunk,
                frames=1600,
                time_info=None,
                status=None
            )

        # Queue size must strictly not exceed MAX_QUEUE_SIZE (100)
        self.assertLessEqual(service._audio_queue.qsize(), service.MAX_QUEUE_SIZE)
        service.stop()

    def test_device_failure_graceful_handling(self):
        service = AudioCaptureService()
        # Mock sounddevice raising PortAudioError on open
        with patch("sounddevice.InputStream", side_effect=RuntimeError("Device unplugged")):
            success = service.start()
            self.assertFalse(success)
            self.assertIn("Device unplugged", service.last_error)
            self.assertFalse(service.is_capturing())

    def test_vad_energy_detection(self):
        service = AudioCaptureService()
        # Silence (low energy)
        silence = [10] * 1600
        is_speech1, rms1 = service.detect_voice_activity(silence)
        self.assertFalse(is_speech1)
        self.assertLess(rms1, service.VAD_RMS_THRESHOLD)

        # Active speech (high energy)
        speech = generate_harmonic_pcm(300.0, duration_s=0.1)
        is_speech2, rms2 = service.detect_voice_activity(speech)
        self.assertTrue(is_speech2)
        self.assertGreaterEqual(rms2, service.VAD_RMS_THRESHOLD)


class TestJarvisWindowsSpeakerVerification(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.profile_path = os.path.join(self.temp_dir.name, "test_profile.json")
        self.manager = SpeakerProfileManager(profile_path=self.profile_path)

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_empty_audio_rejected(self):
        # Empty list
        verified, score, reason = self.manager.verify_speaker([])
        self.assertFalse(verified)
        self.assertEqual(score, 0.0)
        self.assertIn("empty", reason.lower())

        # None audio
        verified_none, score_none, reason_none = self.manager.verify_speaker(None)
        self.assertFalse(verified_none)
        self.assertEqual(score_none, 0.0)

    def test_short_duration_audio_rejected(self):
        # 2000 samples = 0.125s at 16kHz (minimum 0.5s required)
        short_pcm = generate_harmonic_pcm(400.0, duration_s=0.125)
        verified, score, reason = self.manager.verify_speaker(short_pcm)
        self.assertFalse(verified)
        self.assertIn("too short", reason.lower())

    def test_silent_audio_rejected(self):
        # 0.8s of silence (RMS < 220)
        silent_pcm = [15] * 12800
        verified, score, reason = self.manager.verify_speaker(silent_pcm)
        self.assertFalse(verified)
        self.assertIn("too low", reason.lower())

    def test_multi_sample_enrollment_and_genuine_verification(self):
        self.assertFalse(self.manager.enabled)

        # Enrolled speaker profile: 440 Hz harmonic voice
        sample1 = generate_harmonic_pcm(440.0, duration_s=0.8)
        pass1, ready1, _ = self.manager.add_enrollment_sample(sample1)
        self.assertEqual(pass1, 1)
        self.assertFalse(ready1)

        sample2 = generate_harmonic_pcm(440.0, duration_s=0.8)
        pass2, ready2, _ = self.manager.add_enrollment_sample(sample2)
        self.assertEqual(pass2, 2)
        self.assertFalse(ready2)

        sample3 = generate_harmonic_pcm(440.0, duration_s=0.8)
        pass3, ready3, _ = self.manager.add_enrollment_sample(sample3)
        self.assertEqual(pass3, 3)
        self.assertTrue(ready3)

        success, msg = self.manager.finalize_enrollment()
        self.assertTrue(success)
        self.assertTrue(self.manager.enabled)
        self.assertIsNotNone(self.manager.enrolled_template)
        self.assertEqual(len(self.manager.enrolled_template), 16)

        # 1. Matching speaker (440 Hz) -> Verified with high similarity
        matching_sample = generate_harmonic_pcm(440.0, duration_s=0.8)
        verified_match, score_match, _ = self.manager.verify_speaker(matching_sample)
        self.assertTrue(verified_match, "Matching speaker must be verified")
        self.assertGreaterEqual(score_match, self.manager.threshold)

        # 2. Non-matching speaker with completely different pitch (150 Hz male voice vs 440 Hz) -> Rejected
        different_sample = generate_harmonic_pcm(150.0, duration_s=0.8)
        verified_diff, score_diff, _ = self.manager.verify_speaker(different_sample)
        self.assertFalse(verified_diff, "Different voice profile must be rejected")
        self.assertLess(score_diff, score_match)


class TestJarvisWindowsAiFallback(unittest.TestCase):

    def test_ai_engine_unavailable_returns_explicit_failure_no_canned_success(self):
        engine = AiQueryEngine()

        # Mock urllib to raise URLError for both OpenJarvis and Ollama
        with patch("urllib.request.urlopen", side_effect=urllib.error.URLError("Connection refused")):
            response = engine.query("What are my pending tasks?")

            # Must return explicit failure state
            self.assertFalse(response.success)
            self.assertEqual(response.source, "NONE")
            self.assertEqual(response.response_text, "")
            self.assertIsNotNone(response.error_message)
            self.assertIn("No AI engine available", response.error_message)

            # Strict requirement: Never report canned success
            self.assertNotIn("Affirmative", response.response_text)
            self.assertNotIn("operational", response.response_text)

    def test_ai_engine_openjarvis_success_attribution(self):
        engine = AiQueryEngine()
        mock_resp_data = json.dumps({
            "choices": [{"message": {"content": "You have 2 pending tasks scheduled today."}}]
        }).encode("utf-8")

        mock_http_response = MagicMock()
        mock_http_response.read.return_value = mock_resp_data
        mock_http_response.__enter__.return_value = mock_http_response

        with patch("urllib.request.urlopen", return_value=mock_http_response):
            response = engine.query("What are my pending tasks?")
            self.assertTrue(response.success)
            self.assertEqual(response.source, "OPENJARVIS_SERVER")
            self.assertEqual(response.response_text, "You have 2 pending tasks scheduled today.")

    def test_ai_engine_ollama_fallback_attribution(self):
        engine = AiQueryEngine()

        def side_effect(req, timeout=None):
            url = req.full_url if hasattr(req, 'full_url') else req
            if "8000" in url:
                # OpenJarvis fails
                raise urllib.error.URLError("Port 8000 closed")
            elif "11434" in url:
                # Ollama succeeds
                mock_resp = MagicMock()
                mock_resp.read.return_value = json.dumps({
                    "response": "Meeting with design team at 4pm."
                }).encode("utf-8")
                mock_resp.__enter__.return_value = mock_resp
                return mock_resp
            raise urllib.error.URLError("Unknown url")

        with patch("urllib.request.urlopen", side_effect=side_effect):
            response = engine.query("What are my meetings?")
            self.assertTrue(response.success)
            self.assertEqual(response.source, "LOCAL_OLLAMA")
            self.assertEqual(response.response_text, "Meeting with design team at 4pm.")


class TestJarvisWindowsServiceLifecycle(unittest.TestCase):

    def test_service_start_pause_resume_stop(self):
        assistant = JarvisWindowsAssistant()
        self.assertEqual(assistant.state, STATE_IDLE)

        # Start
        started = assistant.start()
        if started:
            self.assertEqual(assistant.state, STATE_WAKE_LISTENING)
            self.assertTrue(assistant.running)
            self.assertFalse(assistant.paused)

            # Prevent duplicate audio capture threads on repeated start()
            second_start = assistant.start()
            self.assertTrue(second_start)

            # Pause
            assistant.pause()
            self.assertEqual(assistant.state, STATE_PAUSED)
            self.assertTrue(assistant.paused)

            # Resume
            assistant.resume()
            self.assertEqual(assistant.state, STATE_WAKE_LISTENING)
            self.assertFalse(assistant.paused)

            # Stop
            assistant.stop()
            self.assertEqual(assistant.state, STATE_STOPPED)
            self.assertFalse(assistant.running)
            self.assertIsNone(assistant._listener_thread)
        else:
            assistant.stop()

    def test_wake_event_speaker_gating(self):
        assistant = JarvisWindowsAssistant()
        assistant.running = True
        assistant.state = STATE_WAKE_LISTENING

        # Enable speaker verification
        assistant.speaker_manager.enabled = True
        assistant.speaker_manager.enrolled_template = [1.0] + [0.0] * 15

        # Trigger wake event with empty audio -> must reject at speaker verification
        assistant.trigger_wake_event(pcm_wake_chunk=[], command_text="Open files")
        # Must return to WAKE_LISTENING without proceeding to COMMAND_LISTENING
        self.assertEqual(assistant.state, STATE_WAKE_LISTENING)

        assistant.stop()


if __name__ == "__main__":
    unittest.main()

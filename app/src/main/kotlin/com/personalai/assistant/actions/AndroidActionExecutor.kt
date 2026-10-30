package com.personalai.assistant.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Locale

enum class MediaAction {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS
}

sealed class AssistantAction {
    data class LaunchApp(val appLabel: String, val targetPackage: String? = null) : AssistantAction()
    data class OpenSettingsScreen(val settingType: String, val intentAction: String) : AssistantAction()
    data class DialPhone(val recipient: String, val phoneNumber: String) : AssistantAction()
    data class WebSearch(val searchQuery: String) : AssistantAction()
    data class NavigateTo(val location: String) : AssistantAction()
    data class SendMessage(val recipient: String, val body: String) : AssistantAction()
    data class InAppDestination(val route: String, val title: String) : AssistantAction()
    data class SetTimer(val durationSeconds: Int, val label: String) : AssistantAction()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : AssistantAction()
    object ShowAlarms : AssistantAction()
    data class ToggleFlashlight(val enable: Boolean) : AssistantAction()
    object CheckBatteryStatus : AssistantAction()
    data class MediaControl(val mediaAction: MediaAction) : AssistantAction()
    data class AddCalendarEvent(val title: String, val startEpochMs: Long? = null) : AssistantAction()
    object None : AssistantAction()
}

data class ActionResult(
    val handled: Boolean,
    val action: AssistantAction,
    val spokenFeedback: String,
    val launchIntent: Intent? = null,
    val inAppRoute: String? = null
)

/**
 * Intelligent Android Action Registry & Intent Dispatcher.
 *
 * Provides deterministic, offline, zero-latency phone control for both English
 * and Hinglish voice instructions without external cloud APIs:
 * - App launch & Settings navigation
 * - Alarms, Timers & Stopwatch
 * - Flashlight / Torch hardware control
 * - Battery percentage & charging status reporting
 * - Media playback (Play, Pause, Skip)
 * - Dialer, SMS, Web Search & Maps Navigation
 */
class AndroidActionExecutor(private val context: Context) {

    companion object {
        private const val TAG = "AndroidActionExecutor"

        // Well-known app package mappings for fast deterministic resolution
        private val POPULAR_APP_PACKAGES = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "instagram" to "com.instagram.android",
            "telegram" to "org.telegram.messenger",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "facebook" to "com.facebook.katana",
            "linkedin" to "com.linkedin.android",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "photos" to "com.google.android.apps.photos",
            "gallery" to "com.google.android.apps.photos",
            "drive" to "com.google.android.apps.docs",
            "files" to "com.google.android.apps.nbu.files"
        )
    }

    /**
     * Parses a spoken voice utterance into an executable Android action.
     * Supports both English and Hinglish expressions.
     */
    fun parseAction(query: String): AssistantAction {
        val trimmed = query.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
            .replace(Regex("[.,?!]+$"), "")
            .trim()

        if (lower.isEmpty()) return AssistantAction.None

        // 1. In-App Destinations
        when {
            lower in setOf("open recordings", "show recordings", "recordings dikhao", "recordings kholo") ->
                return AssistantAction.InAppDestination("recordings", "Recordings")
            lower in setOf("open tasks", "show tasks", "tasks dikhao", "kaam dikhao", "pending tasks") ->
                return AssistantAction.InAppDestination("tasks", "Tasks")
            lower in setOf("open meetings", "show meetings", "meetings dikhao", "meetings kholo") ->
                return AssistantAction.InAppDestination("meetings", "Meetings")
            lower in setOf("open memory", "show memory", "memory screen", "notes dikhao") ->
                return AssistantAction.InAppDestination("memory", "Personal Memory")
            lower in setOf("open diagnostics", "voice diagnostics", "audio health", "diagnostics dikhao") ->
                return AssistantAction.InAppDestination("diagnostics", "Voice Diagnostics")
        }

        // 2. Battery Status Reporting
        if (isBatteryQuery(lower)) {
            return AssistantAction.CheckBatteryStatus
        }

        // 3. Flashlight / Torch Control
        val flashMatch = matchFlashlightIntent(lower)
        if (flashMatch != null) return flashMatch

        // 4. Timer Intents
        val timerMatch = matchTimerIntent(trimmed, lower)
        if (timerMatch != null) return timerMatch

        // 5. Alarm Intents
        val alarmMatch = matchAlarmIntent(trimmed, lower)
        if (alarmMatch != null) return alarmMatch

        // 6. Media / Music Playback Controls
        val mediaMatch = matchMediaIntent(lower)
        if (mediaMatch != null) return mediaMatch

        // 7. Calendar Event Creation
        val calMatch = matchCalendarIntent(trimmed, lower)
        if (calMatch != null) return calMatch

        // 8. Settings Intents
        if (isSettingsIntent(lower)) {
            return resolveSettingsAction(lower)
        }

        // 9. Dial Phone Intent
        val dialMatch = matchDialIntent(trimmed, lower)
        if (dialMatch != null) return dialMatch

        // 10. Web Search Intent
        val searchMatch = matchSearchIntent(trimmed, lower)
        if (searchMatch != null) return searchMatch

        // 11. Navigation / Maps Intent
        val navMatch = matchNavigationIntent(trimmed, lower)
        if (navMatch != null) return navMatch

        // 12. Camera Shortcut
        if (lower in setOf("open camera", "camera open karo", "camera kholo", "take a picture", "take photo", "photo khicho")) {
            return AssistantAction.LaunchApp("Camera")
        }

        // 13. General App Launch Intent
        val appMatch = matchAppLaunchIntent(trimmed, lower)
        if (appMatch != null) return appMatch

        return AssistantAction.None
    }

    /**
     * Executes the parsed assistant action on Android and returns human/spoken feedback.
     */
    fun execute(action: AssistantAction): ActionResult {
        return when (action) {
            is AssistantAction.InAppDestination -> {
                ActionResult(
                    handled = true,
                    action = action,
                    spokenFeedback = "Navigating to ${action.title}, Commander.",
                    inAppRoute = action.route
                )
            }

            is AssistantAction.ToggleFlashlight -> {
                try {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                    if (cameraManager != null && cameraId != null) {
                        cameraManager.setTorchMode(cameraId, action.enable)
                        val stateStr = if (action.enable) "turned on" else "turned off"
                        ActionResult(
                            handled = true,
                            action = action,
                            spokenFeedback = "Flashlight $stateStr, Commander."
                        )
                    } else {
                        ActionResult(
                            handled = false,
                            action = action,
                            spokenFeedback = "Flashlight hardware is unavailable on this device."
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle flashlight: ${e.message}")
                    ActionResult(
                        handled = false,
                        action = action,
                        spokenFeedback = "I could not toggle the flashlight."
                    )
                }
            }

            is AssistantAction.CheckBatteryStatus -> {
                try {
                    val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    val batteryStatus = context.registerReceiver(null, ifilter)
                    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

                    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    val chargeStatusStr = if (isCharging) "and currently charging" else "not charging"

                    if (batteryPct >= 0) {
                        ActionResult(
                            handled = true,
                            action = action,
                            spokenFeedback = "Battery level is at $batteryPct percent, $chargeStatusStr, Commander."
                        )
                    } else {
                        ActionResult(
                            handled = true,
                            action = action,
                            spokenFeedback = "Battery power level is normal, Commander."
                        )
                    }
                } catch (e: Exception) {
                    ActionResult(
                        handled = false,
                        action = action,
                        spokenFeedback = "Battery status information is unavailable."
                    )
                }
            }

            is AssistantAction.SetTimer -> {
                try {
                    val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, action.durationSeconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(timerIntent)

                    val minutes = action.durationSeconds / 60
                    val seconds = action.durationSeconds % 60
                    val timeStr = when {
                        minutes > 0 && seconds > 0 -> "$minutes minute and $seconds seconds"
                        minutes > 0 -> "$minutes minute"
                        else -> "$seconds seconds"
                    }
                    ActionResult(
                        handled = true,
                        action = action,
                        spokenFeedback = "Setting a timer for $timeStr, Commander.",
                        launchIntent = timerIntent
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set timer", e)
                    ActionResult(false, action, "Could not set timer on this device.")
                }
            }

            is AssistantAction.SetAlarm -> {
                try {
                    val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(alarmIntent)

                    val period = if (action.hour >= 12) "PM" else "AM"
                    val displayHour = when {
                        action.hour == 0 -> 12
                        action.hour > 12 -> action.hour - 12
                        else -> action.hour
                    }
                    val timeStr = String.format(Locale.getDefault(), "%d:%02d %s", displayHour, action.minute, period)
                    ActionResult(
                        handled = true,
                        action = action,
                        spokenFeedback = "Alarm set for $timeStr, Commander.",
                        launchIntent = alarmIntent
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set alarm", e)
                    ActionResult(false, action, "Could not set alarm on this device.")
                }
            }

            is AssistantAction.ShowAlarms -> {
                try {
                    val showIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(showIntent)
                    ActionResult(
                        handled = true,
                        action = action,
                        spokenFeedback = "Opening alarms, Commander.",
                        launchIntent = showIntent
                    )
                } catch (e: Exception) {
                    ActionResult(false, action, "Could not open alarms.")
                }
            }

            is AssistantAction.MediaControl -> {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    when (action.mediaAction) {
                        MediaAction.PLAY -> {
                            val playIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(playIntent)
                            ActionResult(true, action, "Playing music, Commander.", playIntent)
                        }
                        MediaAction.PAUSE -> {
                            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
                            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
                            ActionResult(true, action, "Music paused, Commander.")
                        }
                        MediaAction.NEXT -> {
                            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
                            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
                            ActionResult(true, action, "Skipping to next track, Commander.")
                        }
                        MediaAction.PREVIOUS -> {
                            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                            ActionResult(true, action, "Playing previous track, Commander.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Media control failed", e)
                    ActionResult(false, action, "Could not control media playback.")
                }
            }

            is AssistantAction.AddCalendarEvent -> {
                try {
                    val calIntent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, action.title)
                        if (action.startEpochMs != null) {
                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.startEpochMs)
                        }
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(calIntent)
                    ActionResult(
                        handled = true,
                        action = action,
                        spokenFeedback = "Opening calendar to schedule '${action.title}', Commander.",
                        launchIntent = calIntent
                    )
                } catch (e: Exception) {
                    ActionResult(false, action, "Could not open calendar event scheduler.")
                }
            }

            is AssistantAction.OpenSettingsScreen -> {
                try {
                    val intent = Intent(action.intentAction).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ActionResult(
                        handled = true,
                        action = action,
                        spokenFeedback = "Opening ${action.settingType} settings, Commander.",
                        launchIntent = intent
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open settings: ${action.settingType}", e)
                    ActionResult(
                        handled = false,
                        action = action,
                        spokenFeedback = "I could not open ${action.settingType} settings on this device."
                    )
                }
            }

            is AssistantAction.LaunchApp -> {
                if (action.appLabel.equals("Camera", ignoreCase = true)) {
                    return try {
                        val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(camIntent)
                        ActionResult(
                            handled = true,
                            action = action,
                            spokenFeedback = "Opening Camera, Commander.",
                            launchIntent = camIntent
                        )
                    } catch (e: Exception) {
                        ActionResult(false, action, "Camera could not be launched.")
                    }
                }

                val packageName = action.targetPackage ?: resolvePackageName(action.appLabel)
                if (packageName != null) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                            ActionResult(
                                handled = true,
                                action = action,
                                spokenFeedback = "Opening ${action.appLabel}, Commander.",
                                launchIntent = launchIntent
                            )
                        } else {
                            ActionResult(
                                handled = false,
                                action = action,
                                spokenFeedback = "${action.appLabel} is installed but could not be launched directly."
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching app: $packageName", e)
                        ActionResult(false, action, "Failed to open ${action.appLabel}.")
                    }
                } else {
                    ActionResult(
                        handled = false,
                        action = action,
                        spokenFeedback = "I could not find ${action.appLabel} installed on your device."
                    )
                }
            }

            is AssistantAction.DialPhone -> {
                try {
                    val uri = Uri.parse("tel:${Uri.encode(action.phoneNumber)}")
                    val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(dialIntent)
                    ActionResult(
                        handled = true,
                        action = action,
                        spokenFeedback = "Opening dialer for ${action.recipient}, Commander.",
                        launchIntent = dialIntent
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching dialer", e)
                    ActionResult(false, action, "I could not open the phone dialer.")
                }
            }

            is AssistantAction.WebSearch -> {
                try {
                    val encoded = URLEncoder.encode(action.searchQuery, StandardCharsets.UTF_8.toString())
                    val url = "https://www.google.com/search?q=$encoded"
                    val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(searchIntent)
                    ActionResult(
                        handled = true,
                        action = action,
                        spokenFeedback = "Searching Google for ${action.searchQuery}, Commander.",
                        launchIntent = searchIntent
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening web search", e)
                    ActionResult(false, action, "I could not launch the web browser.")
                }
            }

            is AssistantAction.NavigateTo -> {
                try {
                    val encoded = URLEncoder.encode(action.location, StandardCharsets.UTF_8.toString())
                    val geoUri = Uri.parse("geo:0,0?q=$encoded")
                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(mapIntent)
                    ActionResult(
                        handled = true,
                        action = action,
                        spokenFeedback = "Opening directions to ${action.location}, Commander.",
                        launchIntent = mapIntent
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening map navigation", e)
                    ActionResult(false, action, "Could not open map navigation.")
                }
            }

            is AssistantAction.SendMessage -> {
                try {
                    val smsUri = Uri.parse("smsto:${Uri.encode(action.recipient)}")
                    val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                        putExtra("sms_body", action.body)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(smsIntent)
                    ActionResult(
                        handled = true,
                        action = action,
                        spokenFeedback = "Preparing message for ${action.recipient}.",
                        launchIntent = smsIntent
                    )
                } catch (e: Exception) {
                    ActionResult(false, action, "Could not open messaging app.")
                }
            }

            AssistantAction.None -> {
                ActionResult(
                    handled = false,
                    action = AssistantAction.None,
                    spokenFeedback = ""
                )
            }
        }
    }

    private fun isBatteryQuery(lower: String): Boolean {
        return lower in setOf("battery level", "battery percentage", "battery status", "how much battery", "battery kitni hai", "check battery", "battery check karo") ||
                lower.startsWith("what is my battery") || lower.startsWith("what's my battery") ||
                lower.startsWith("battery kitni") || lower.contains("battery percentage")
    }

    private fun matchFlashlightIntent(lower: String): AssistantAction.ToggleFlashlight? {
        if (lower in setOf("turn on flashlight", "turn on torch", "flashlight on", "torch on", "torch on karo", "flashlight on karo", "flashlight chalu karo")) {
            return AssistantAction.ToggleFlashlight(true)
        }
        if (lower in setOf("turn off flashlight", "turn off torch", "flashlight off", "torch off", "torch off karo", "flashlight off karo", "flashlight band karo")) {
            return AssistantAction.ToggleFlashlight(false)
        }
        return null
    }

    private fun matchTimerIntent(original: String, lower: String): AssistantAction.SetTimer? {
        val timerEnglish = Regex("^set (?:a )?timer for (\\d+)\\s*(second|seconds|sec|minute|minutes|min|hour|hours|ghante)?", RegexOption.IGNORE_CASE)
        val timerHinglish = Regex("^(\\d+)\\s*(minute|second|min|ghante)\\s*ka timer lagao", RegexOption.IGNORE_CASE)

        val match = timerEnglish.find(lower) ?: timerHinglish.find(lower)
        if (match != null) {
            val num = match.groupValues[1].toIntOrNull() ?: return null
            val unit = if (match.groupValues.size > 2) match.groupValues[2].lowercase(Locale.ROOT) else "minute"
            val totalSeconds = when {
                unit.startsWith("sec") -> num
                unit.startsWith("hour") || unit.startsWith("ghant") -> num * 3600
                else -> num * 60
            }
            return AssistantAction.SetTimer(durationSeconds = totalSeconds, label = "JARVIS Timer")
        }
        return null
    }

    private fun matchAlarmIntent(original: String, lower: String): AssistantAction? {
        if (lower in setOf("show alarms", "open alarms", "alarm dikhao", "alarm kholo")) {
            return AssistantAction.ShowAlarms
        }

        // English: "set alarm for 7:30 am", "set alarm for 8 am", "set alarm for 6 pm"
        val alarmEnglish = Regex("^set (?:an )?alarm for (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        val engMatch = alarmEnglish.find(lower)
        if (engMatch != null) {
            var hour = engMatch.groupValues[1].toIntOrNull() ?: return null
            val minute = engMatch.groupValues.getOrNull(2)?.ifEmpty { "0" }?.toIntOrNull() ?: 0
            val ampm = engMatch.groupValues.getOrNull(3)?.lowercase(Locale.ROOT)

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            return AssistantAction.SetAlarm(hour = hour, minute = minute, label = "JARVIS Alarm")
        }

        // Hinglish: "subah 7 baje ka alarm lagao", "shaam 8 baje ka alarm lagao", "7:30 baje ka alarm lagao"
        val alarmHinglish = Regex("^(subah|shaam|dophar|raat)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(?:baje)?\\s*ka alarm lagao", RegexOption.IGNORE_CASE)
        val hinMatch = alarmHinglish.find(lower)
        if (hinMatch != null) {
            val periodWord = hinMatch.groupValues[1].lowercase(Locale.ROOT)
            var hour = hinMatch.groupValues[2].toIntOrNull() ?: return null
            val minute = hinMatch.groupValues.getOrNull(3)?.ifEmpty { "0" }?.toIntOrNull() ?: 0

            if ((periodWord == "shaam" || periodWord == "raat") && hour < 12) hour += 12
            if (periodWord == "subah" && hour == 12) hour = 0

            return AssistantAction.SetAlarm(hour = hour, minute = minute, label = "JARVIS Alarm")
        }

        return null
    }

    private fun matchMediaIntent(lower: String): AssistantAction.MediaControl? {
        return when {
            lower in setOf("play music", "play song", "play songs", "gana bajao", "resume music", "sangeet bajao") ->
                AssistantAction.MediaControl(MediaAction.PLAY)
            lower in setOf("pause music", "stop music", "pause song", "gana roko", "gana band karo", "music band karo") ->
                AssistantAction.MediaControl(MediaAction.PAUSE)
            lower in setOf("next song", "next track", "skip song", "agla gana", "change song") ->
                AssistantAction.MediaControl(MediaAction.NEXT)
            lower in setOf("previous song", "previous track", "pichhla gana") ->
                AssistantAction.MediaControl(MediaAction.PREVIOUS)
            else -> null
        }
    }

    private fun matchCalendarIntent(original: String, lower: String): AssistantAction.AddCalendarEvent? {
        val prefixes = listOf("add calendar event ", "create event ", "add event ", "schedule event ")
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                val title = original.substring(prefix.length).trim()
                if (title.isNotEmpty()) return AssistantAction.AddCalendarEvent(title)
            }
        }
        if (lower.endsWith(" event add karo") || lower.endsWith(" calendar mein add karo")) {
            val title = original.replace(Regex("(event add karo|calendar mein add karo)$", RegexOption.IGNORE_CASE), "").trim()
            if (title.isNotEmpty()) return AssistantAction.AddCalendarEvent(title)
        }
        return null
    }

    private fun isSettingsIntent(lower: String): Boolean {
        return lower.contains("settings") || lower.contains("setting") ||
                lower.startsWith("open wifi") || lower.startsWith("open wi-fi") ||
                lower.startsWith("open bluetooth") || lower.contains("bluetooth kholo") ||
                lower.contains("wifi on karo") || lower.contains("settings kholo")
    }

    private fun resolveSettingsAction(lower: String): AssistantAction.OpenSettingsScreen {
        return when {
            lower.contains("wifi") || lower.contains("wi-fi") ->
                AssistantAction.OpenSettingsScreen("Wi-Fi", Settings.ACTION_WIFI_SETTINGS)
            lower.contains("bluetooth") ->
                AssistantAction.OpenSettingsScreen("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS)
            lower.contains("display") || lower.contains("brightness") ->
                AssistantAction.OpenSettingsScreen("Display", Settings.ACTION_DISPLAY_SETTINGS)
            lower.contains("sound") || lower.contains("volume") || lower.contains("audio") ->
                AssistantAction.OpenSettingsScreen("Sound", Settings.ACTION_SOUND_SETTINGS)
            lower.contains("battery") ->
                AssistantAction.OpenSettingsScreen("Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS)
            lower.contains("app") || lower.contains("applications") ->
                AssistantAction.OpenSettingsScreen("Applications", Settings.ACTION_APPLICATION_SETTINGS)
            lower.contains("storage") ->
                AssistantAction.OpenSettingsScreen("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            lower.contains("date") || lower.contains("time") ->
                AssistantAction.OpenSettingsScreen("Date & Time", Settings.ACTION_DATE_SETTINGS)
            else ->
                AssistantAction.OpenSettingsScreen("Device", Settings.ACTION_SETTINGS)
        }
    }

    private fun matchDialIntent(original: String, lower: String): AssistantAction.DialPhone? {
        val dialRegex = Regex("^(call|dial|phone|call karo)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val hinglishCallRegex = Regex("^(.+?)\\s+(ko call karo|ko dial karo|ko phone lagao)$", RegexOption.IGNORE_CASE)

        val recipient = when {
            dialRegex.containsMatchIn(lower) -> {
                dialRegex.find(original)?.groupValues?.get(2)?.trim()
            }
            hinglishCallRegex.containsMatchIn(lower) -> {
                hinglishCallRegex.find(original)?.groupValues?.get(1)?.trim()
            }
            else -> null
        }

        if (recipient.isNullOrBlank()) return null
        val cleanDigits = recipient.filter { it.isDigit() || it == '+' }
        val finalNumber = if (cleanDigits.length >= 3) cleanDigits else recipient
        return AssistantAction.DialPhone(recipient = recipient, phoneNumber = finalNumber)
    }

    private fun matchSearchIntent(original: String, lower: String): AssistantAction.WebSearch? {
        val searchPrefixes = listOf(
            "search for ", "search ", "google search for ", "google ", "web search for ",
            "find on google ", "internet pe search karo "
        )
        for (prefix in searchPrefixes) {
            if (lower.startsWith(prefix)) {
                val q = original.substring(prefix.length).trim()
                if (q.isNotEmpty()) return AssistantAction.WebSearch(q)
            }
        }
        if (lower.endsWith(" search karo") || lower.endsWith(" google karo")) {
            val q = original.replace(Regex("(search karo|google karo)$", RegexOption.IGNORE_CASE), "").trim()
            if (q.isNotEmpty()) return AssistantAction.WebSearch(q)
        }
        return null
    }

    private fun matchNavigationIntent(original: String, lower: String): AssistantAction.NavigateTo? {
        val navPrefixes = listOf(
            "navigate to ", "directions to ", "route to ", "take me to ", "maps to "
        )
        for (prefix in navPrefixes) {
            if (lower.startsWith(prefix)) {
                val loc = original.substring(prefix.length).trim()
                if (loc.isNotEmpty()) return AssistantAction.NavigateTo(loc)
            }
        }
        if (lower.endsWith(" ka rasta dikhao") || lower.endsWith(" ki directions")) {
            val loc = original.replace(Regex("(ka rasta dikhao|ki directions)$", RegexOption.IGNORE_CASE), "").trim()
            if (loc.isNotEmpty()) return AssistantAction.NavigateTo(loc)
        }
        return null
    }

    private fun matchAppLaunchIntent(original: String, lower: String): AssistantAction.LaunchApp? {
        val launchPrefixes = listOf("open ", "launch ", "start ")
        var targetAppName: String? = null

        for (prefix in launchPrefixes) {
            if (lower.startsWith(prefix)) {
                targetAppName = original.substring(prefix.length).trim()
                break
            }
        }

        if (targetAppName == null && (lower.endsWith(" open karo") || lower.endsWith(" kholo") || lower.endsWith(" start karo"))) {
            targetAppName = original.replace(Regex("(open karo|kholo|start karo)$", RegexOption.IGNORE_CASE), "").trim()
        }

        if (targetAppName.isNullOrBlank()) return null
        val cleanLabel = targetAppName.replace(Regex("[.,?!]"), "").trim()
        val pkg = resolvePackageName(cleanLabel)
        return AssistantAction.LaunchApp(appLabel = cleanLabel, targetPackage = pkg)
    }

    /**
     * Resolves an app label to an installed package name using well-known map or PackageManager.
     */
    fun resolvePackageName(label: String): String? {
        val lower = label.lowercase(Locale.ROOT).trim()

        // 1. Direct hit on popular apps map
        if (POPULAR_APP_PACKAGES.containsKey(lower)) {
            val candidate = POPULAR_APP_PACKAGES[lower]!!
            if (isPackageInstalled(candidate)) return candidate
        }

        // 2. Query PackageManager for dynamic app labels installed on this phone
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                // Ignore system background apps without launch intent
                if (pm.getLaunchIntentForPackage(app.packageName) == null) continue

                val appName = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                if (appName == lower || appName.contains(lower) || lower.contains(appName)) {
                    return app.packageName
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting installed applications: ${e.message}")
        }

        return null
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}

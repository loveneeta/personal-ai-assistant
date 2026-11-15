package com.personalai.assistant.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized Android Permission & Privacy Manager for J.A.R.V.I.S.
 *
 * Tracks, inspects, and manages all runtime and special system permissions
 * required for full-phone executive capabilities, maintaining auditable access
 * timestamps and direct revocation instructions.
 */
class JarvisPermissionManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "jarvis_permissions_audit"
        private const val KEY_PREFIX_TIMESTAMP = "last_access_"

        @Volatile
        private var INSTANCE: JarvisPermissionManager? = null

        fun getInstance(context: Context): JarvisPermissionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JarvisPermissionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _permissionsState = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissionsState: StateFlow<List<PermissionItem>> = _permissionsState.asStateFlow()

    init {
        refreshPermissions()
    }

    /**
     * Refreshes the granted status of all permissions across categories.
     */
    fun refreshPermissions() {
        val items = listOf(
            // 1. Audio & Surroundings
            createPermissionItem(
                id = "audio_record",
                title = "Omni-Directional Microphone",
                category = PermissionCategory.AUDIO,
                manifestPermissions = listOf(Manifest.permission.RECORD_AUDIO),
                purpose = "Allows JARVIS to listen for wake words and process voice commands.",
                dataAccessible = "Real-time acoustic audio streams (processed strictly on-device)."
            ),

            // 2. Notification Access (Special Permission for WhatsApp / Messaging)
            createSpecialPermissionItem(
                id = "notification_listener",
                title = "Notification Listener Service",
                category = PermissionCategory.NOTIFICATIONS,
                isGranted = isNotificationListenerGranted(),
                settingsAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
                purpose = "Enables JARVIS to read incoming WhatsApp, Gmail, and messaging alerts to draft replies.",
                dataAccessible = "Notification titles, senders, and message preview bodies."
            ),

            // 3. Post Notifications (Android 13+)
            createPermissionItem(
                id = "post_notifications",
                title = "System Notifications",
                category = PermissionCategory.NOTIFICATIONS,
                manifestPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                },
                purpose = "Delivers action approval prompts, daily briefings, and status alerts.",
                dataAccessible = "Displays notifications on your status bar."
            ),

            // 4. Contacts Directory
            createPermissionItem(
                id = "contacts",
                title = "Phonebook & Contacts",
                category = PermissionCategory.CONTACTS,
                manifestPermissions = listOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
                ),
                purpose = "Allows JARVIS to resolve contact names for phone calls, WhatsApp, and SMS instructions.",
                dataAccessible = "Names, phone numbers, and email addresses stored in your phone."
            ),

            // 5. Calendar & Schedule
            createPermissionItem(
                id = "calendar",
                title = "Calendar & Meetings",
                category = PermissionCategory.CALENDAR,
                manifestPermissions = listOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                ),
                purpose = "Enables schedule conflict detection, reading upcoming meetings, and staging calendar events.",
                dataAccessible = "Calendar event titles, descriptions, dates, and attendee lists."
            ),

            // 6. SMS & Messages
            createPermissionItem(
                id = "sms",
                title = "SMS Communications",
                category = PermissionCategory.COMMUNICATIONS,
                manifestPermissions = listOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS
                ),
                purpose = "Reads incoming SMS messages for summaries and drafts outbound SMS upon user approval.",
                dataAccessible = "SMS conversation text and sender phone numbers."
            ),

            // 7. Phone Calls & Call Log
            createPermissionItem(
                id = "phone_calls",
                title = "Phone & Call Manager",
                category = PermissionCategory.COMMUNICATIONS,
                manifestPermissions = listOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CALL_LOG
                ),
                purpose = "Initiates voice phone calls and summarizes recent call activity when requested.",
                dataAccessible = "Call history records and telephony dialer control."
            )
        )

        _permissionsState.value = items
    }

    private fun createPermissionItem(
        id: String,
        title: String,
        category: PermissionCategory,
        manifestPermissions: List<String>,
        purpose: String,
        dataAccessible: String
    ): PermissionItem {
        val granted = if (manifestPermissions.isEmpty()) {
            true
        } else {
            manifestPermissions.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        }

        val lastAccessed = prefs.getLong(KEY_PREFIX_TIMESTAMP + id, 0L).let { if (it > 0) it else null }

        return PermissionItem(
            id = id,
            title = title,
            category = category,
            manifestPermissions = manifestPermissions,
            purpose = purpose,
            dataAccessible = dataAccessible,
            isGranted = granted,
            isSpecialPermission = false,
            lastAccessedTimestamp = lastAccessed
        )
    }

    private fun createSpecialPermissionItem(
        id: String,
        title: String,
        category: PermissionCategory,
        isGranted: Boolean,
        settingsAction: String,
        purpose: String,
        dataAccessible: String
    ): PermissionItem {
        val lastAccessed = prefs.getLong(KEY_PREFIX_TIMESTAMP + id, 0L).let { if (it > 0) it else null }

        return PermissionItem(
            id = id,
            title = title,
            category = category,
            manifestPermissions = emptyList(),
            purpose = purpose,
            dataAccessible = dataAccessible,
            isGranted = isGranted,
            isSpecialPermission = true,
            settingsAction = settingsAction,
            lastAccessedTimestamp = lastAccessed
        )
    }

    /**
     * Checks if notification listener access is enabled for this application.
     */
    fun isNotificationListenerGranted(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    /**
     * Records an audit timestamp whenever a permission is exercised by an agent.
     */
    fun recordPermissionAccess(permissionId: String) {
        prefs.edit().putLong(KEY_PREFIX_TIMESTAMP + permissionId, System.currentTimeMillis()).apply()
        refreshPermissions()
    }

    /**
     * Creates an Intent to navigate to the system application details screen so the user can easily revoke permissions.
     */
    fun createAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Creates an Intent to open the Android Notification Listener Settings page.
     */
    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}

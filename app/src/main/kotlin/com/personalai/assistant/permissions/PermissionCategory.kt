package com.personalai.assistant.permissions

/**
 * Functional categories for JARVIS's executive phone permissions.
 */
enum class PermissionCategory(val displayName: String) {
    AUDIO("Audio & Surroundings"),
    NOTIFICATIONS("Notifications & Messaging"),
    COMMUNICATIONS("SMS & Phone Calls"),
    CONTACTS("Contacts Directory"),
    CALENDAR("Calendar & Schedule"),
    STORAGE("Files & Storage Access")
}

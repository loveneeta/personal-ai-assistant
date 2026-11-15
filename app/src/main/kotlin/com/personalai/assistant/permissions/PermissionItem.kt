package com.personalai.assistant.permissions

/**
 * Representation of an individual system capability, showing purpose,
 * data accessible, current status, and revocation instructions.
 */
data class PermissionItem(
    val id: String,
    val title: String,
    val category: PermissionCategory,
    val manifestPermissions: List<String>,
    val purpose: String,
    val dataAccessible: String,
    val isGranted: Boolean,
    val isSpecialPermission: Boolean = false,
    val settingsAction: String? = null,
    val lastAccessedTimestamp: Long? = null
)

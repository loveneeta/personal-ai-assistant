package com.personalai.assistant.permissions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JarvisPermissionManagerTest {

    private lateinit var context: Context
    private lateinit var permissionManager: JarvisPermissionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        permissionManager = JarvisPermissionManager.getInstance(context)
    }

    @Test
    fun testPermissionsList_containsAllRequiredCategories() {
        permissionManager.refreshPermissions()
        val items = permissionManager.permissionsState.value

        assertTrue("Expected permission items to not be empty", items.isNotEmpty())

        val categories = items.map { it.category }.toSet()
        assertTrue("Should contain AUDIO category", categories.contains(PermissionCategory.AUDIO))
        assertTrue("Should contain NOTIFICATIONS category", categories.contains(PermissionCategory.NOTIFICATIONS))
        assertTrue("Should contain COMMUNICATIONS category", categories.contains(PermissionCategory.COMMUNICATIONS))
        assertTrue("Should contain CONTACTS category", categories.contains(PermissionCategory.CONTACTS))
        assertTrue("Should contain CALENDAR category", categories.contains(PermissionCategory.CALENDAR))
    }

    @Test
    fun testRecordPermissionAccess_updatesTimestamp() {
        val testPermId = "audio_record"
        permissionManager.recordPermissionAccess(testPermId)

        val item = permissionManager.permissionsState.value.firstOrNull { it.id == testPermId }
        assertNotNull("Expected permission item '$testPermId' to exist", item)
        assertNotNull("Expected timestamp to be recorded", item?.lastAccessedTimestamp)
        assertTrue("Expected valid timestamp", (item?.lastAccessedTimestamp ?: 0) > 0)
    }

    @Test
    fun testIntentCreation_createsValidIntents() {
        val appSettingsIntent = permissionManager.createAppSettingsIntent()
        assertNotNull(appSettingsIntent)
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", appSettingsIntent.action)

        val notifSettingsIntent = permissionManager.createNotificationListenerSettingsIntent()
        assertNotNull(notifSettingsIntent)
        assertEquals("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", notifSettingsIntent.action)
    }
}

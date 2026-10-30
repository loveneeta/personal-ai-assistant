package com.personalai.assistant.actions

import android.content.Context
import android.provider.AlarmClock
import android.provider.Settings
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
class AndroidActionExecutorTest {

    private lateinit var context: Context
    private lateinit var executor: AndroidActionExecutor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        executor = AndroidActionExecutor(context)
    }

    @Test
    fun testAppLaunchParsing_EnglishAndHinglish() {
        // English: "open WhatsApp"
        val action1 = executor.parseAction("open WhatsApp")
        assertTrue(action1 is AssistantAction.LaunchApp)
        assertEquals("WhatsApp", (action1 as AssistantAction.LaunchApp).appLabel)

        // Hinglish: "YouTube open karo"
        val action2 = executor.parseAction("YouTube open karo")
        assertTrue(action2 is AssistantAction.LaunchApp)
        assertEquals("YouTube", (action2 as AssistantAction.LaunchApp).appLabel)

        // Hinglish: "Chrome kholo"
        val action3 = executor.parseAction("Chrome kholo")
        assertTrue(action3 is AssistantAction.LaunchApp)
        assertEquals("Chrome", (action3 as AssistantAction.LaunchApp).appLabel)

        // Camera direct intent
        val action4 = executor.parseAction("camera kholo")
        assertTrue(action4 is AssistantAction.LaunchApp)
        assertEquals("Camera", (action4 as AssistantAction.LaunchApp).appLabel)
    }

    @Test
    fun testSettingsIntentParsing() {
        // General settings
        val s1 = executor.parseAction("open settings")
        assertTrue(s1 is AssistantAction.OpenSettingsScreen)
        assertEquals(Settings.ACTION_SETTINGS, (s1 as AssistantAction.OpenSettingsScreen).intentAction)

        // Wi-Fi settings
        val s2 = executor.parseAction("open wifi settings")
        assertTrue(s2 is AssistantAction.OpenSettingsScreen)
        assertEquals(Settings.ACTION_WIFI_SETTINGS, (s2 as AssistantAction.OpenSettingsScreen).intentAction)

        // Bluetooth settings in Hinglish
        val s3 = executor.parseAction("bluetooth kholo")
        assertTrue(s3 is AssistantAction.OpenSettingsScreen)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, (s3 as AssistantAction.OpenSettingsScreen).intentAction)

        // Sound settings
        val s4 = executor.parseAction("open volume settings")
        assertTrue(s4 is AssistantAction.OpenSettingsScreen)
        assertEquals(Settings.ACTION_SOUND_SETTINGS, (s4 as AssistantAction.OpenSettingsScreen).intentAction)
    }

    @Test
    fun testDialerIntentParsing() {
        // Direct number
        val d1 = executor.parseAction("call 9876543210")
        assertTrue(d1 is AssistantAction.DialPhone)
        val dial1 = d1 as AssistantAction.DialPhone
        assertEquals("9876543210", dial1.phoneNumber)

        // Contact in Hinglish
        val d2 = executor.parseAction("Rohan ko call karo")
        assertTrue(d2 is AssistantAction.DialPhone)
        val dial2 = d2 as AssistantAction.DialPhone
        assertEquals("Rohan", dial2.recipient)

        // Dial prefix
        val d3 = executor.parseAction("dial +911234567890")
        assertTrue(d3 is AssistantAction.DialPhone)
        val dial3 = d3 as AssistantAction.DialPhone
        assertEquals("+911234567890", dial3.phoneNumber)
    }

    @Test
    fun testWebSearchAndNavigationParsing() {
        // Web Search
        val s1 = executor.parseAction("search for flight tickets to Mumbai")
        assertTrue(s1 is AssistantAction.WebSearch)
        assertEquals("flight tickets to Mumbai", (s1 as AssistantAction.WebSearch).searchQuery)

        // Google search in Hinglish
        val s2 = executor.parseAction("today weather search karo")
        assertTrue(s2 is AssistantAction.WebSearch)
        assertEquals("today weather", (s2 as AssistantAction.WebSearch).searchQuery)

        // Map navigation
        val n1 = executor.parseAction("navigate to Connaught Place")
        assertTrue(n1 is AssistantAction.NavigateTo)
        assertEquals("Connaught Place", (n1 as AssistantAction.NavigateTo).location)

        // Map navigation in Hinglish
        val n2 = executor.parseAction("Airport ka rasta dikhao")
        assertTrue(n2 is AssistantAction.NavigateTo)
        assertEquals("Airport", (n2 as AssistantAction.NavigateTo).location)
    }

    @Test
    fun testInAppNavigationParsing() {
        val r1 = executor.parseAction("open recordings")
        assertTrue(r1 is AssistantAction.InAppDestination)
        assertEquals("recordings", (r1 as AssistantAction.InAppDestination).route)

        val r2 = executor.parseAction("show tasks")
        assertTrue(r2 is AssistantAction.InAppDestination)
        assertEquals("tasks", (r2 as AssistantAction.InAppDestination).route)

        val r3 = executor.parseAction("open meetings")
        assertTrue(r3 is AssistantAction.InAppDestination)
        assertEquals("meetings", (r3 as AssistantAction.InAppDestination).route)

        val r4 = executor.parseAction("open diagnostics")
        assertTrue(r4 is AssistantAction.InAppDestination)
        assertEquals("diagnostics", (r4 as AssistantAction.InAppDestination).route)
    }

    @Test
    fun testTimerParsing_EnglishAndHinglish() {
        // English: "set timer for 5 minutes"
        val t1 = executor.parseAction("set timer for 5 minutes")
        assertTrue(t1 is AssistantAction.SetTimer)
        assertEquals(300, (t1 as AssistantAction.SetTimer).durationSeconds)

        // Hinglish: "10 second ka timer lagao"
        val t2 = executor.parseAction("10 second ka timer lagao")
        assertTrue(t2 is AssistantAction.SetTimer)
        assertEquals(10, (t2 as AssistantAction.SetTimer).durationSeconds)

        // Execution of timer returns intent with AlarmClock action
        val result = executor.execute(t1)
        assertTrue(result.handled)
        assertTrue(result.spokenFeedback.contains("5 minute"))
        assertEquals(AlarmClock.ACTION_SET_TIMER, result.launchIntent?.action)
    }

    @Test
    fun testAlarmParsing_EnglishAndHinglish() {
        // English: "set alarm for 7:30 am"
        val a1 = executor.parseAction("set alarm for 7:30 am")
        assertTrue(a1 is AssistantAction.SetAlarm)
        val alarm1 = a1 as AssistantAction.SetAlarm
        assertEquals(7, alarm1.hour)
        assertEquals(30, alarm1.minute)

        // Hinglish: "subah 7 baje ka alarm lagao"
        val a2 = executor.parseAction("subah 7 baje ka alarm lagao")
        assertTrue(a2 is AssistantAction.SetAlarm)
        val alarm2 = a2 as AssistantAction.SetAlarm
        assertEquals(7, alarm2.hour)
        assertEquals(0, alarm2.minute)

        // Hinglish: "shaam 8 baje ka alarm lagao" -> 20:00
        val a3 = executor.parseAction("shaam 8 baje ka alarm lagao")
        assertTrue(a3 is AssistantAction.SetAlarm)
        val alarm3 = a3 as AssistantAction.SetAlarm
        assertEquals(20, alarm3.hour)

        // Show alarms
        val a4 = executor.parseAction("show alarms")
        assertTrue(a4 is AssistantAction.ShowAlarms)
    }

    @Test
    fun testFlashlightParsing() {
        val f1 = executor.parseAction("turn on flashlight")
        assertTrue(f1 is AssistantAction.ToggleFlashlight)
        assertTrue((f1 as AssistantAction.ToggleFlashlight).enable)

        val f2 = executor.parseAction("torch off karo")
        assertTrue(f2 is AssistantAction.ToggleFlashlight)
        assertTrue(!(f2 as AssistantAction.ToggleFlashlight).enable)
    }

    @Test
    fun testBatteryStatusQuery() {
        val b1 = executor.parseAction("what is my battery level?")
        assertTrue(b1 is AssistantAction.CheckBatteryStatus)

        val b2 = executor.parseAction("battery kitni hai?")
        assertTrue(b2 is AssistantAction.CheckBatteryStatus)

        val result = executor.execute(b1)
        assertTrue(result.handled)
        assertTrue(result.spokenFeedback.contains("Battery"))
    }

    @Test
    fun testMediaControls() {
        val m1 = executor.parseAction("play music")
        assertTrue(m1 is AssistantAction.MediaControl)
        assertEquals(MediaAction.PLAY, (m1 as AssistantAction.MediaControl).mediaAction)

        val m2 = executor.parseAction("gana band karo")
        assertTrue(m2 is AssistantAction.MediaControl)
        assertEquals(MediaAction.PAUSE, (m2 as AssistantAction.MediaControl).mediaAction)

        val m3 = executor.parseAction("next song")
        assertTrue(m3 is AssistantAction.MediaControl)
        assertEquals(MediaAction.NEXT, (m3 as AssistantAction.MediaControl).mediaAction)
    }

    @Test
    fun testCalendarEventParsing() {
        val c1 = executor.parseAction("add event Team Sync")
        assertTrue(c1 is AssistantAction.AddCalendarEvent)
        assertEquals("Team Sync", (c1 as AssistantAction.AddCalendarEvent).title)

        val c2 = executor.parseAction("Product Review event add karo")
        assertTrue(c2 is AssistantAction.AddCalendarEvent)
        assertEquals("Product Review", (c2 as AssistantAction.AddCalendarEvent).title)
    }

    @Test
    fun testNonActionFallsBackToNone() {
        // General conversational / RAG queries should NOT trigger actions
        val nonAction1 = executor.parseAction("Kal customer ki meeting hai")
        assertEquals(AssistantAction.None, nonAction1)

        val nonAction2 = executor.parseAction("What was discussed in yesterday's meeting?")
        assertEquals(AssistantAction.None, nonAction2)

        val nonAction3 = executor.parseAction("Add task review financial report")
        // "Add task" is handled by LocalRagChatEngine's task creation protocol, not an Android OS app action
        assertEquals(AssistantAction.None, nonAction3)
    }

    @Test
    fun testExecutionReturnsProperIntentAndSpokenFeedback() {
        val searchAction = AssistantAction.WebSearch("Kotlin coroutines")
        val result = executor.execute(searchAction)

        assertTrue(result.handled)
        assertTrue(result.spokenFeedback.contains("Searching Google for Kotlin coroutines"))
        assertNotNull(result.launchIntent)
    }
}

package com.example

import android.content.Context
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.test.core.app.ApplicationProvider
import com.example.devicepolicy.AndroidDevicePolicyController
import com.example.domain.model.LockSchedule
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.model.ScheduleType
import com.example.domain.scheduling.ScheduleCalculator
import com.example.domain.time.ClockProvider
import com.example.platform.ForegroundEnforcementGuard
import com.example.platform.InstalledAppsRepository
import com.example.platform.SystemCapabilityChecker
import com.example.presentation.components.SafeAndroidTextToolbar
import com.example.scheduling.AndroidLockAlarmScheduler
import com.example.security.RecoveryCodeManager
import com.example.telephony.PhoneAccessManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Verifies legacy Android compatibility paths on the lowest SDK supported by Robolectric 4.16 (SDK 23),
 * exercising pre-Oreo (API < 26) and pre-Pie (API < 28) code paths alongside PBKDF2-HMAC-SHA1 fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class Api21CompatRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `system capabilities, alarms, device policy, phone access, and apps work on legacy SDK`() = runTest {
        val checker = SystemCapabilityChecker(context)
        val status = checker.queryStatus()
        assertEquals(23, status.androidSdkInt)
        assertEquals(false, status.exactAlarmRequiredByOs)

        val phoneManager = PhoneAccessManager(context)
        phoneManager.resolveEssentialDialerPackages()
        phoneManager.resolveEmergencyPackages()

        val appsRepo = InstalledAppsRepository(context, phoneManager)
        appsRepo.loadLaunchableApps()

        val dpmController = AndroidDevicePolicyController(context, phoneManager, appsRepo)
        assertEquals(false, dpmController.isDeviceOwner())
        assertEquals(0, dpmController.getLockTaskModeState())
        assertTrue(dpmController.getAppliedLockTaskPackages().isEmpty())

        val guard = ForegroundEnforcementGuard(context, checker)
        guard.queryLatestForegroundPackage()
        guard.clearBarrier()

        val scheduler = AndroidLockAlarmScheduler(context)
        scheduler.scheduleDelayedLockTransition("legacy-s1", System.currentTimeMillis() + 60_000L)
        scheduler.scheduleLockExpiration("legacy-s1", System.currentTimeMillis() + 120_000L)
        scheduler.cancelAllSessionAlarms()
    }

    @Test
    fun `recovery code manager and PBKDF2WithHmacSHA1 fallback work cleanly on legacy SDK`() = runTest {
        val app = context as ResolveApplication
        val clock = object : ClockProvider {
            override fun wallClockMs(): Long = 1_794_745_000_000L
            override fun elapsedRealtimeMs(): Long = 5_000L
            override fun zoneId(): ZoneId = ZoneId.of("UTC")
        }
        val manager = RecoveryCodeManager(app.container.preferencesRepository, clock)
        val code = "1234567890123456"
        assertTrue(manager.saveRecoveryCode(code))
        assertEquals(RecoveryVerificationResult.Verified, manager.verifyRecoveryCode(code))

        val spec = PBEKeySpec(code.toCharArray(), ByteArray(16) { 1 }, 1_000, 256)
        val sha1Derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        assertEquals(32, sha1Derived.size)
    }

    @Test
    fun `safe text toolbar shows and hides safely on legacy SDK`() {
        val view = View(context)
        val toolbar = SafeAndroidTextToolbar(view)
        toolbar.showMenu(
            rect = Rect(0f, 0f, 50f, 20f),
            onCopyRequested = {},
            onPasteRequested = {},
            onCutRequested = null,
            onSelectAllRequested = {}
        )
        toolbar.hide()
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        toolbar.release()
    }

    @Test
    fun `java time schedule calculator executes cleanly on legacy SDK`() {
        val schedule = LockSchedule(
            id = 1L,
            name = "Morning Study",
            type = ScheduleType.DAILY,
            startHour = 8,
            startMinute = 30,
            lockDurationMinutes = 45L,
            allowedPackages = emptyList(),
            phoneAccessEnabled = true,
            isEnabled = true,
            createdAtWallMs = 1_794_745_000_000L
        )
        val next = ScheduleCalculator.computeNextTriggerWallMs(
            schedule = schedule,
            nowWallMs = 1_794_745_000_000L,
            zoneId = ZoneId.of("UTC")
        )
        assertTrue(next != null && next > 1_794_745_000_000L)
    }
}

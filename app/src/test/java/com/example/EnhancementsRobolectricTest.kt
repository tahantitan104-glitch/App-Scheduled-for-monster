package com.example

import android.app.Activity
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ResolveDatabase
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.repository.AllowlistRepository
import com.example.data.repository.DelayedPresetRepository
import com.example.data.repository.FlipTimerPresetRepository
import com.example.data.repository.HistoryRepository
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.SessionRepository
import com.example.designsystem.ResolveTheme
import com.example.devicepolicy.DevicePolicyController
import com.example.domain.model.AmbientSoundType
import com.example.domain.model.ContentBlockerConfig
import com.example.domain.model.EnforcementMode
import com.example.domain.model.EnforcementPreparationResult
import com.example.domain.model.FlipClockSettings
import com.example.domain.model.LockMode
import com.example.domain.model.RecoveryVerificationResult
import com.example.domain.time.ClockProvider
import com.example.flipclock.FlipClockManager
import com.example.flipclock.FlipClockSettingsSheet
import com.example.flipclock.FlipTimerNotificationHelper
import com.example.flipclock.FlipWeatherRepository
import com.example.flipclock.FlipWeatherResult
import com.example.flipclock.FlipWhiteNoiseStudioSheet
import com.example.platform.SystemCapabilityChecker
import com.example.presentation.lockscreen.SignatureLockScreen
import com.example.scheduling.LockAlarmScheduler
import com.example.security.RecoveryCodeManager
import com.example.session.LockSessionEngine
import com.example.session.SessionPlatformBridge
import com.example.sync.CloudAuthSession
import com.example.sync.CloudDeviceInfo
import com.example.sync.CloudFlipClockSettings
import com.example.sync.CloudFlipTimerPreset
import com.example.sync.CloudLockCommand
import com.example.sync.CloudLockProfileSettings
import com.example.sync.CloudSyncGateway
import com.example.sync.CloudUserProfile
import com.example.sync.MultiDeviceSyncCoordinator
import com.example.vpn.DnsBlocklistMatcher
import com.example.vpn.DnsPacketCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnhancementsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ResolveDatabase
    private lateinit var prefs: ResolvePreferencesRepository
    private lateinit var allowlistRepo: AllowlistRepository
    private lateinit var delayedPresetRepo: DelayedPresetRepository
    private lateinit var scheduleRepo: ScheduleRepository
    private lateinit var flipPresetRepo: FlipTimerPresetRepository
    private lateinit var clock: FakeEnhancementClock
    private lateinit var engine: LockSessionEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FlipWeatherRepository.clearMemoryCacheForTests()
        db = Room.inMemoryDatabaseBuilder(context, ResolveDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = (context as ResolveApplication).container.preferencesRepository
        allowlistRepo = AllowlistRepository(db.allowedAppDao())
        delayedPresetRepo = DelayedPresetRepository(db.delayedLockPresetDao())
        scheduleRepo = ScheduleRepository(db.lockScheduleDao())
        flipPresetRepo = FlipTimerPresetRepository(db.flipTimerPresetDao(), prefs)
        clock = FakeEnhancementClock(wallMs = 1_796_000_000_000L, elapsedMs = 80_000L)

        val recovery = RecoveryCodeManager(prefs, clock)
        engine = LockSessionEngine(
            sessionRepository = SessionRepository(db.activeSessionDao()),
            scheduleRepository = scheduleRepo,
            historyRepository = HistoryRepository(db.sessionHistoryDao()),
            preferencesRepository = prefs,
            devicePolicyController = FakeEnhancementDevicePolicy(),
            alarmScheduler = NoOpEnhancementAlarms(),
            recoveryCodeManager = recovery,
            capabilityChecker = SystemCapabilityChecker(context),
            clockProvider = clock,
            platformBridge = NoOpEnhancementBridge()
        )
    }

    @After
    fun tearDown() {
        engine.shutdown()
        db.close()
    }

    // ==================== ITEM 1: WEATHER UPGRADE ====================

    @Test
    fun `weather formatting converts celsius to fahrenheit and marks cached offline entries`() {
        val freezing = FlipWeatherResult.Ready("London", 0, "Cloudy")
        assertEquals(32, freezing.temperatureFahrenheit)
        assertEquals("32°F · Cloudy · London", freezing.formatLabel(useFahrenheit = true, compact = false))
        assertEquals("0°C · Cloudy", freezing.formatLabel(useFahrenheit = false, compact = true))

        val warmCached = FlipWeatherResult.Ready("Dhaka", 25, "Clear", isCachedOffline = true)
        assertEquals(77, warmCached.temperatureFahrenheit)
        assertEquals("77°F · Clear · Dhaka (cached)", warmCached.formatLabel(useFahrenheit = true))
        assertEquals("25°C · Clear (cached)", warmCached.formatLabel(useFahrenheit = false, compact = true))
    }

    @Test
    fun `weather city auto-detection parses IP JSON and extracts readable timezone city`() {
        assertEquals("Dhaka", FlipWeatherRepository.extractCityFromTimeZoneId("Asia/Dhaka"))
        assertEquals("New York", FlipWeatherRepository.extractCityFromTimeZoneId("America/New_York"))
        assertNull(FlipWeatherRepository.extractCityFromTimeZoneId("UTC"))

        assertEquals(
            "Tokyo",
            FlipWeatherRepository.parseAutoDetectedCityJson("""{"city":"Tokyo","regionName":"Tokyo"}""")
        )
        assertEquals(
            "California",
            FlipWeatherRepository.parseAutoDetectedCityJson("""{"city":"","regionName":"California"}""")
        )
    }

    @Test
    fun `weather disk cache round-trips cleanly and serves offline fallback when network is unavailable`() = runTest {
        val sample = FlipWeatherResult.Ready(
            cityLabel = "OfflineCachedCity",
            temperatureCelsius = 28,
            conditionLabel = "Mostly Clear",
            fetchedAtWallMs = System.currentTimeMillis()
        )
        val encoded = FlipWeatherRepository.encodeWeatherCacheJson("OfflineCachedCity", sample)
        val decoded = FlipWeatherRepository.decodeWeatherCacheJson(encoded)
        assertNotNull(decoded)
        assertEquals("OfflineCachedCity", decoded!!.first)
        assertEquals(28, decoded.second.temperatureCelsius)

        // 1. Fresh disk cache hit without network call
        context.getSharedPreferences("resolve_weather_cache", Context.MODE_PRIVATE)
            .edit()
            .putString("last_weather_json", encoded)
            .commit()

        val cachedHit = FlipWeatherRepository.fetchWeather(
            cityQuery = "OfflineCachedCity",
            autoDetect = false,
            context = context,
            forceRefresh = false
        )
        assertTrue(cachedHit is FlipWeatherResult.Ready)
        assertEquals(28, (cachedHit as FlipWeatherResult.Ready).temperatureCelsius)
        assertFalse(cachedHit.isCachedOffline)

        // 2. Forced refresh fails lookup -> serves offline "(cached)" fallback from disk/memory
        FlipWeatherRepository.clearMemoryCacheForTests()
        val offlineFallback = FlipWeatherRepository.fetchWeather(
            cityQuery = "OfflineCachedCity",
            autoDetect = false,
            context = context,
            forceRefresh = true
        )
        assertTrue(offlineFallback is FlipWeatherResult.Ready)
        val readyFallback = offlineFallback as FlipWeatherResult.Ready
        assertEquals("OfflineCachedCity", readyFallback.cityLabel)
        assertEquals(28, readyFallback.temperatureCelsius)
        assertTrue(readyFallback.isCachedOffline)
    }

    // ==================== ITEM 2: WAKEFUL LOCK SYNC & OUTBOX ====================

    @Test
    fun `offline outbound lock commands queue persistently and flush cleanly once gateway recovers`() = runTest {
        val gateway = FlakySyncCloudGateway(failPublish = true)
        val flipManager = FlipClockManager(prefs, flipPresetRepo, FlipTimerNotificationHelper(context), engine, clock)
        val coordinator = MultiDeviceSyncCoordinator(
            appContext = context,
            gateway = gateway,
            preferencesRepository = prefs,
            allowlistRepository = allowlistRepo,
            sessionEngine = engine,
            clockProvider = clock,
            delayedPresetRepository = delayedPresetRepo,
            scheduleRepository = scheduleRepo,
            flipTimerPresetRepository = flipPresetRepo,
            flipClockManager = flipManager
        )
        prefs.setSyncLockEnabled(true)
        coordinator.start()
        ShadowLooper.idleMainLooper()

        // Simulate publish while offline -> command is queued persistently
        coordinator.publishLockTriggerIfEnabled(
            mode = LockMode.INSTANT,
            freeUseMinutes = 0L,
            lockDurationMinutes = 20L,
            freeUseDeadlineWallMs = 0L,
            lockExpirationWallMs = clock.wallMs + 20 * 60_000L,
            syncAcrossDevices = true,
            strictMode = false,
            showFlipClockShortcut = false
        )
        assertEquals(1, coordinator.pendingLockCommandsCount())
        assertTrue(gateway.publishedCommands.isEmpty())

        // Restore connectivity and run wakeful sync check -> queued command flushes and outbox clears
        gateway.failPublish = false
        coordinator.performWakefulSyncCheck()
        assertEquals(0, coordinator.pendingLockCommandsCount())
        assertEquals(1, gateway.publishedCommands.size)
        assertEquals("INSTANT", gateway.publishedCommands.first().mode)
        assertEquals(20L, gateway.publishedCommands.first().lockDurationMinutes)
    }

    // ==================== ITEM 4: LOCAL VPN DNS BLOCKER ====================

    @Test
    fun `dns packet codec parses IPv4 UDP DNS query and builds valid NXDOMAIN response`() {
        val queryPacket = buildSampleIpv4UdpDnsQueryPacket(
            domain = "m.tiktok.com",
            txId = 0x4A2B,
            srcIp = byteArrayOf(10, 111, 222.toByte(), 1),
            dstIp = byteArrayOf(10, 111, 222.toByte(), 2),
            srcPort = 51234
        )
        val parsed = DnsPacketCodec.parseIpv4UdpDnsQuery(queryPacket, queryPacket.size)
        assertNotNull(parsed)
        assertEquals("m.tiktok.com", parsed!!.queryDomain)
        assertEquals(0x4A2B, parsed.transactionId)
        assertEquals(51234, parsed.srcPort)
        assertEquals(53, parsed.dstPort)

        val nxDomain = DnsPacketCodec.buildNxDomainResponse(parsed)
        // IPv4 total length matches packet size & swapped dst/src IPs
        assertEquals(nxDomain.size, ((nxDomain[2].toInt() and 0xFF) shl 8) or (nxDomain[3].toInt() and 0xFF))
        assertEquals(2, nxDomain[15].toInt() and 0xFF) // srcIp becomes 10.111.222.2
        assertEquals(1, nxDomain[19].toInt() and 0xFF) // dstIp becomes 10.111.222.1
        // UDP ports swapped: src=53, dst=51234
        val respSrcPort = ((nxDomain[20].toInt() and 0xFF) shl 8) or (nxDomain[21].toInt() and 0xFF)
        val respDstPort = ((nxDomain[22].toInt() and 0xFF) shl 8) or (nxDomain[23].toInt() and 0xFF)
        assertEquals(53, respSrcPort)
        assertEquals(51234, respDstPort)
        // DNS flags: QR=1, RCODE=3 (NXDOMAIN)
        val dnsFlags = ((nxDomain[30].toInt() and 0xFF) shl 8) or (nxDomain[31].toInt() and 0xFF)
        assertTrue((dnsFlags and 0x8000) != 0)
        assertEquals(3, dnsFlags and 0x000F)
        // IPv4 header checksum validates to 0
        assertEquals(0, DnsPacketCodec.computeIpv4Checksum(nxDomain, 0, 20))
    }

    @Test
    fun `dns blocklist matcher enforces subdomains, categories, and focus lock gating`() {
        assertEquals("www.reddit.com", DnsBlocklistMatcher.normalizeDomain("HTTPS://WWW.Reddit.com/r/android?foo=1"))

        val config = ContentBlockerConfig(
            enabled = true,
            vpnDnsBlockerEnabled = true,
            blockedDomains = listOf("reddit.com"),
            blockShortVideoDomains = true,
            blockSocialDomains = false,
            blockAdultDomains = true,
            onlyDuringFocusLock = true
        )

        // When Focus Lock is inactive and onlyDuringFocusLock=true, DNS blocking stays dormant
        assertFalse(DnsBlocklistMatcher.shouldBlockDomain("m.reddit.com", config, isFocusLockActive = false))

        // When Focus Lock is active: custom subdomains & enabled categories block, unblocked domains pass
        assertTrue(DnsBlocklistMatcher.shouldBlockDomain("m.reddit.com", config, isFocusLockActive = true))
        assertTrue(DnsBlocklistMatcher.shouldBlockDomain("reddit.com", config, isFocusLockActive = true))
        assertFalse(DnsBlocklistMatcher.shouldBlockDomain("notreddit.com", config, isFocusLockActive = true))
        assertTrue(DnsBlocklistMatcher.shouldBlockDomain("api.tiktok.com", config, isFocusLockActive = true))
        assertTrue(DnsBlocklistMatcher.shouldBlockDomain("www.pornhub.com", config, isFocusLockActive = true))
        assertFalse(DnsBlocklistMatcher.shouldBlockDomain("github.com", config, isFocusLockActive = true))
    }

    @Test
    fun `content blocker config and dns blocked counter persist accurately`() = runTest {
        prefs.resetDnsBlockedCount()
        assertEquals(0, prefs.dnsBlockedCountFlow.first())

        val savedCfg = ContentBlockerConfig(
            enabled = true,
            blockYouTubeShorts = true,
            blockInstagramReels = false,
            blockedDomains = listOf("x.com"),
            onlyDuringFocusLock = false,
            vpnDnsBlockerEnabled = true,
            blockAdultDomains = true,
            blockShortVideoDomains = true,
            blockSocialDomains = false
        )
        prefs.saveContentBlockerConfig(savedCfg)
        val loaded = prefs.contentBlockerConfigFlow.first()
        assertEquals(savedCfg, loaded)

        prefs.incrementDnsBlockedCount()
        assertEquals(1, prefs.dnsBlockedCountFlow.first())
        prefs.incrementDnsBlockedCount()
        assertEquals(2, prefs.dnsBlockedCountFlow.first())
        prefs.resetDnsBlockedCount()
        assertEquals(0, prefs.dnsBlockedCountFlow.first())
    }

    // ==================== ITEM 5: AMBIENT SOUNDS & LOCK SYNC ====================

    @Test
    fun `new soundscapes and flip clock sync settings persist and lock screen cycling activates focus playback`() = runTest {
        val flipManager = FlipClockManager(prefs, flipPresetRepo, FlipTimerNotificationHelper(context), engine, clock)
        flipManager.initialize()

        flipManager.setWeatherUseFahrenheit(true)
        flipManager.setWeatherAutoDetect(true)
        flipManager.setSyncFlipTimerWithFocusLock(true)
        flipManager.setPlayAmbientSoundDuringFocusLock(true)
        flipManager.setAmbientSoundType(AmbientSoundType.FOREST_WIND)

        val snap = prefs.getFlipClockSettingsSnapshot()
        assertTrue(snap.weatherUseFahrenheit)
        assertTrue(snap.weatherAutoDetect)
        assertTrue(snap.syncFlipTimerWithFocusLock)
        assertTrue(snap.playAmbientSoundDuringFocusLock)
        assertEquals(AmbientSoundType.FOREST_WIND, snap.ambientSoundType)

        // Cycle from FOREST_WIND -> FOCUS_PAD -> OFF -> WHITE_NOISE
        flipManager.cycleAmbientSoundFromLockScreen()
        assertEquals(AmbientSoundType.FOCUS_PAD, prefs.getFlipClockSettingsSnapshot().ambientSoundType)
        flipManager.cycleAmbientSoundFromLockScreen()
        assertEquals(AmbientSoundType.OFF, prefs.getFlipClockSettingsSnapshot().ambientSoundType)
        flipManager.cycleAmbientSoundFromLockScreen()
        assertEquals(AmbientSoundType.WHITE_NOISE, prefs.getFlipClockSettingsSnapshot().ambientSoundType)
        assertTrue(prefs.getFlipClockSettingsSnapshot().playAmbientSoundDuringFocusLock)

        // Reset restores defaults cleanly
        flipManager.resetSettingsToDefaultPreservingCustomPresets()
        val resetSnap = prefs.getFlipClockSettingsSnapshot()
        assertFalse(resetSnap.weatherUseFahrenheit)
        assertFalse(resetSnap.weatherAutoDetect)
        assertFalse(resetSnap.syncFlipTimerWithFocusLock)
        assertFalse(resetSnap.playAmbientSoundDuringFocusLock)
        assertEquals(AmbientSoundType.OFF, resetSnap.ambientSoundType)
    }

    @Test
    fun `signature lock screen displays tappable ambient soundscape pill when enabled`() {
        var cycled = 0
        composeRule.setContent {
            ResolveTheme {
                SignatureLockScreen(
                    remainingMillis = 15 * 60_000L,
                    progressFraction = 0.75f,
                    lockMode = LockMode.INSTANT,
                    enforcementMode = EnforcementMode.ACCESSIBILITY_GUARD,
                    phoneAccessEnabled = false,
                    allowedApps = emptyList(),
                    expectedRecoveryLength = 16,
                    loadAppIcon = { null },
                    onLaunchPhone = {},
                    onLaunchAllowedApp = {},
                    onSubmitRecoveryCode = { RecoveryVerificationResult.NotConfigured },
                    ambientSoundType = AmbientSoundType.BROWN_NOISE,
                    playAmbientDuringFocus = true,
                    onCycleAmbientSound = { cycled++ }
                )
            }
        }

        composeRule.onNodeWithTag("lock_screen_ambient_pill")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, cycled)
    }

    // ==================== ITEM 6: WHITE NOISE STUDIO & NATURE SOUNDSCAPES ====================

    @Test
    fun `all authentic nature soundscapes round-trip and ambientPlayInBackground persists cleanly`() = runTest {
        val expectedStudioSounds = listOf(
            AmbientSoundType.WHITE_NOISE,
            AmbientSoundType.RAIN,
            AmbientSoundType.BONFIRE,
            AmbientSoundType.FOREST_WIND,
            AmbientSoundType.RIVER_WAVES,
            AmbientSoundType.OCEAN_WAVES,
            AmbientSoundType.THUNDERSTORM,
            AmbientSoundType.BROWN_NOISE,
            AmbientSoundType.WARM_HUM,
            AmbientSoundType.FOCUS_PAD
        )
        assertEquals(expectedStudioSounds.toSet(), AmbientSoundType.studioDisplayOrder.toSet())
        expectedStudioSounds.forEach { sound ->
            assertEquals(sound, AmbientSoundType.fromKey(sound.storageKey))
            assertTrue(sound.label.isNotBlank())
            assertTrue(sound.subtitle.isNotBlank())
        }

        val flipManager = FlipClockManager(prefs, flipPresetRepo, FlipTimerNotificationHelper(context), engine, clock)
        flipManager.initialize()

        flipManager.setAmbientSoundType(AmbientSoundType.BONFIRE)
        flipManager.setAmbientPlayInBackground(true)
        var snap = prefs.getFlipClockSettingsSnapshot()
        assertEquals(AmbientSoundType.BONFIRE, snap.ambientSoundType)
        assertTrue(snap.ambientPlayInBackground)

        flipManager.setAmbientSoundType(AmbientSoundType.RIVER_WAVES)
        snap = prefs.getFlipClockSettingsSnapshot()
        assertEquals(AmbientSoundType.RIVER_WAVES, snap.ambientSoundType)
        assertTrue(snap.ambientPlayInBackground)

        flipManager.stopAmbientSound()
        assertEquals(AmbientSoundType.OFF, prefs.getFlipClockSettingsSnapshot().ambientSoundType)

        flipManager.resetSettingsToDefaultPreservingCustomPresets()
        assertFalse(prefs.getFlipClockSettingsSnapshot().ambientPlayInBackground)
    }

    @Test
    fun `flip white noise studio sheet renders nature cards and handles sound selection and background toggle`() {
        var selectedSound = AmbientSoundType.WHITE_NOISE
        var bgPlayback = false
        var playInFocus = false
        var sleepMins = 0

        composeRule.setContent {
            ResolveTheme {
                FlipWhiteNoiseStudioSheet(
                    settings = FlipClockSettings(
                        ambientSoundType = selectedSound,
                        ambientVolume = 0.55f,
                        ambientPlayInBackground = bgPlayback,
                        playAmbientSoundDuringFocusLock = playInFocus,
                        sleepTimerMinutes = sleepMins
                    ),
                    onDismiss = {},
                    onSelectSound = { selectedSound = it },
                    onVolumeChange = {},
                    onSetPlayInBackground = { bgPlayback = it },
                    onSetPlayDuringFocusLock = { playInFocus = it },
                    onSetSleepTimerMinutes = { sleepMins = it }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("white_noise_studio_sheet").assertIsDisplayed()

        listOf(
            "white_noise_card_white_noise",
            "white_noise_card_rain",
            "white_noise_card_bonfire",
            "white_noise_card_forest_wind",
            "white_noise_card_river_waves",
            "white_noise_card_ocean_waves",
            "white_noise_card_thunderstorm"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }

        composeRule.onNodeWithTag("white_noise_card_bonfire").performScrollTo().performClick()
        assertEquals(AmbientSoundType.BONFIRE, selectedSound)

        composeRule.onNodeWithTag("white_noise_card_river_waves").performScrollTo().performClick()
        assertEquals(AmbientSoundType.RIVER_WAVES, selectedSound)

        composeRule.onNodeWithTag("white_noise_bg_playback_switch").performScrollTo().performClick()
        assertTrue(bgPlayback)

        composeRule.onNodeWithTag("white_noise_focus_lock_switch").performScrollTo().performClick()
        assertTrue(playInFocus)

        composeRule.onNodeWithTag("white_noise_sleep_30").performScrollTo().performClick()
        assertEquals(30, sleepMins)
    }

    @Test
    fun `flip clock settings sheet exposes dedicated white noise studio button`() {
        var openedStudio = 0
        composeRule.setContent {
            ResolveTheme {
                FlipClockSettingsSheet(
                    settings = FlipClockSettings(ambientSoundType = AmbientSoundType.RIVER_WAVES),
                    presets = emptyList(),
                    onDismiss = {},
                    onSetUseSystemTimeFormat = {},
                    onSetUse24Hour = {},
                    onSetHourLeadingZero = {},
                    onSetDateFormatPattern = {},
                    onSetShowFlipAxis = {},
                    onSetShowSeconds = {},
                    onSetShowWeekday = {},
                    onSetShowDate = {},
                    onSetShowBattery = {},
                    onSetShowUpcomingAlarm = {},
                    onSetShowWeather = {},
                    onSetWeatherLocationQuery = {},
                    onSetSleepTimerMinutes = {},
                    onSetClockTickerSound = {},
                    onSetCustomTag = {},
                    onSetKeepScreenAwake = {},
                    onSetAutoRepeat = {},
                    onResetZoom = {},
                    onSavePreset = { _, _, _ -> null },
                    onDeletePreset = {},
                    onResetSettingsOnly = {},
                    onOpenWhiteNoiseStudio = { openedStudio++ }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("flip_open_white_noise_studio_btn")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, openedStudio)
    }

    // ==================== HELPERS ====================

    private fun buildSampleIpv4UdpDnsQueryPacket(
        domain: String,
        txId: Int,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int
    ): ByteArray {
        val qnameBytes = mutableListOf<Byte>()
        domain.split('.').forEach { label ->
            qnameBytes.add(label.length.toByte())
            label.toByteArray(Charsets.US_ASCII).forEach { qnameBytes.add(it) }
        }
        qnameBytes.add(0)

        val dnsLen = 12 + qnameBytes.size + 4
        val udpLen = 8 + dnsLen
        val totalLen = 20 + udpLen
        val buf = ByteArray(totalLen)

        buf[0] = 0x45.toByte()
        buf[2] = ((totalLen ushr 8) and 0xFF).toByte()
        buf[3] = (totalLen and 0xFF).toByte()
        buf[8] = 64.toByte()
        buf[9] = 17.toByte()
        System.arraycopy(srcIp, 0, buf, 12, 4)
        System.arraycopy(dstIp, 0, buf, 16, 4)
        val ipSum = DnsPacketCodec.computeIpv4Checksum(buf, 0, 20)
        buf[10] = ((ipSum ushr 8) and 0xFF).toByte()
        buf[11] = (ipSum and 0xFF).toByte()

        buf[20] = ((srcPort ushr 8) and 0xFF).toByte()
        buf[21] = (srcPort and 0xFF).toByte()
        buf[22] = 0
        buf[23] = 53
        buf[24] = ((udpLen ushr 8) and 0xFF).toByte()
        buf[25] = (udpLen and 0xFF).toByte()

        buf[28] = ((txId ushr 8) and 0xFF).toByte()
        buf[29] = (txId and 0xFF).toByte()
        buf[30] = 0x01 // Standard query with RD=1
        buf[31] = 0x00
        buf[32] = 0x00
        buf[33] = 0x01 // QDCOUNT = 1

        qnameBytes.forEachIndexed { idx, b -> buf[40 + idx] = b }
        val qtypeIdx = 40 + qnameBytes.size
        buf[qtypeIdx + 1] = 1 // QTYPE A
        buf[qtypeIdx + 3] = 1 // QCLASS IN
        return buf
    }
}

private class FakeEnhancementClock(var wallMs: Long, var elapsedMs: Long) : ClockProvider {
    override fun wallClockMs(): Long = wallMs
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

private class FakeEnhancementDevicePolicy : DevicePolicyController {
    override fun isDeviceOwner(): Boolean = false
    override fun getLockTaskModeState(): Int = 0
    override fun getAppliedLockTaskPackages(): List<String> = emptyList()
    override suspend fun prepareLockTaskPolicy(
        selectedPackages: List<String>,
        phoneAccessEnabled: Boolean
    ): EnforcementPreparationResult = EnforcementPreparationResult.Ready(emptyList())
    override fun enterLockTask(activity: Activity): Boolean = false
    override fun exitLockTask(activity: Activity): Boolean = false
    override fun clearLockTaskPolicy() = Unit
}

private class NoOpEnhancementAlarms : LockAlarmScheduler {
    override fun scheduleDelayedLockTransition(sessionId: String, triggerAtWallMs: Long) = Unit
    override fun scheduleLockExpiration(sessionId: String, expireAtWallMs: Long) = Unit
    override fun scheduleNextScheduleTrigger(scheduleId: Long, triggerAtWallMs: Long) = Unit
    override fun cancelDelayedLockTransition() = Unit
    override fun cancelLockExpiration() = Unit
    override fun cancelScheduleTrigger(scheduleId: Long) = Unit
    override fun cancelAllSessionAlarms() = Unit
}

private class NoOpEnhancementBridge : SessionPlatformBridge {
    override fun startMonitorService() = Unit
    override fun stopMonitorService() = Unit
    override fun launchLockScreen() = Unit
    override fun notifyLockStateChanged() = Unit
}

private class FlakySyncCloudGateway(var failPublish: Boolean = false) : CloudSyncGateway {
    private val authFlow = MutableStateFlow<CloudAuthSession?>(
        CloudAuthSession(uid = "uid-test", email = "test@resolve.app", displayName = "Tester")
    )
    val publishedCommands = mutableListOf<CloudLockCommand>()

    override val authState: Flow<CloudAuthSession?> = authFlow
    override val currentUserId: String?
        get() = authFlow.value?.uid

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<CloudAuthSession> =
        Result.success(authFlow.value!!)

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    override fun observeUserProfile(userId: String): Flow<CloudUserProfile?> = emptyFlow()
    override fun observeDevices(userId: String): Flow<List<CloudDeviceInfo>> = emptyFlow()
    override fun observeLatestLockCommand(userId: String): Flow<CloudLockCommand?> = emptyFlow()
    override suspend fun ensureUserProfile(user: CloudAuthSession): Result<Unit> = Result.success(Unit)
    override suspend fun registerOrUpdateDevice(
        userId: String,
        deviceId: String,
        deviceName: String,
        syncEnabled: Boolean
    ): Result<Unit> = Result.success(Unit)

    override suspend fun updateDeviceSyncEnabled(
        userId: String,
        deviceId: String,
        deviceName: String,
        syncEnabled: Boolean
    ): Result<Unit> = Result.success(Unit)

    override suspend fun updateAvatarBase64(userId: String, avatarBase64: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun publishLockCommand(
        userId: String,
        originDeviceId: String,
        originDeviceName: String,
        mode: LockMode,
        freeUseDurationMinutes: Long,
        lockDurationMinutes: Long,
        issuedAtWallMs: Long,
        intendedStartWallMs: Long,
        freeUseDeadlineWallMs: Long,
        lockExpirationWallMs: Long,
        scheduleId: String,
        occurrenceId: String,
        strictMode: Boolean,
        showFlipClockShortcut: Boolean,
        allowSelectedApps: Boolean,
        phoneAccessEnabled: Boolean,
        commandAction: String,
        targetDeviceId: String,
        extendMinutes: Long
    ): Result<String> {
        if (failPublish) {
            return Result.failure(IllegalStateException("Network unavailable"))
        }
        val cmdId = "cmd_${publishedCommands.size + 1}"
        publishedCommands.add(
            CloudLockCommand(
                userId = userId,
                commandId = cmdId,
                originDeviceId = originDeviceId,
                originDeviceName = originDeviceName,
                commandAction = commandAction,
                targetDeviceId = targetDeviceId,
                extendMinutes = extendMinutes,
                mode = mode.name,
                freeUseDurationMinutes = freeUseDurationMinutes,
                lockDurationMinutes = lockDurationMinutes,
                issuedAtWallMs = issuedAtWallMs,
                intendedStartWallMs = intendedStartWallMs,
                freeUseDeadlineWallMs = freeUseDeadlineWallMs,
                lockExpirationWallMs = lockExpirationWallMs,
                scheduleId = scheduleId,
                occurrenceId = occurrenceId,
                strictMode = strictMode,
                showFlipClockShortcut = showFlipClockShortcut,
                allowSelectedApps = allowSelectedApps,
                phoneAccessEnabled = phoneAccessEnabled
            )
        )
        return Result.success(cmdId)
    }
}

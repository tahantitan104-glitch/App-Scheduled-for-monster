package com.example.domain.model

/**
 * Exactly two mutually exclusive mascots supported by RESOLVE.
 * No third mascot and no dual/"Both" mode exist anywhere in the system.
 */
enum class MascotType(
    val storageKey: String,
    val displayName: String,
    val subtitle: String
) {
    BABY_CLOUD(
        storageKey = "baby_cloud",
        displayName = "Baby Cloud",
        subtitle = "Original calm cloud companion"
    ),
    RESOLVE_BUNNY(
        storageKey = "resolve_bunny",
        displayName = "RESOLVE Bunny",
        subtitle = "Programmatic 2.5D rabbit with signature bent ear"
    );

    companion object {
        val DEFAULT: MascotType = BABY_CLOUD

        fun fromStorageKey(key: String?): MascotType =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}

enum class FocusMessageFrequency(
    val storageKey: String,
    val intervalMinutes: Int?,
    val label: String
) {
    EVERY_2_MIN("2m", 2, "Every 2 minutes"),
    EVERY_3_MIN("3m", 3, "Every 3 minutes"),
    EVERY_4_MIN("4m", 4, "Every 4 minutes"),
    EVERY_5_MIN("5m", 5, "Every 5 minutes"),
    DISABLED("disabled", null, "Disabled");

    companion object {
        fun fromStorageKey(key: String?): FocusMessageFrequency =
            entries.firstOrNull { it.storageKey == key } ?: EVERY_3_MIN
    }
}

enum class MascotAnimationState {
    IDLE,
    BLINK,
    BREATHE,
    HEAD_EAR_ATTENTION,
    POINTING,
    CURIOUS,
    TURNING,
    HAPPY_DANCE,
    CELEBRATING,
    PEEK_ENTRANCE,
    GENTLE_FLOAT,
    CURIOUS_SWAY,
    HAPPY_BOUNCE
}

enum class MascotMicroAnimation {
    NONE,
    EAR_WIGGLE,
    SMALL_HOP,
    GENTLE_WAVE,
    SHY,
    SLEEPY
}

data class FocusMessageItem(
    val id: String,
    val text: String,
    val isCustom: Boolean = false
)

object DefaultFocusMessages {
    val items: List<FocusMessageItem> = listOf(
        FocusMessageItem(id = "default_1", text = "Stay focused!", isCustom = false),
        FocusMessageItem(id = "default_2", text = "You're doing great!", isCustom = false),
        FocusMessageItem(id = "default_3", text = "Keep going!", isCustom = false),
        FocusMessageItem(id = "default_4", text = "Don't break your resolve!", isCustom = false),
        FocusMessageItem(id = "default_5", text = "Almost there!", isCustom = false),
        FocusMessageItem(id = "default_6", text = "Your future self will thank you.", isCustom = false),
        FocusMessageItem(id = "default_7", text = "One minute at a time.", isCustom = false),
        FocusMessageItem(id = "default_8", text = "Stay strong!", isCustom = false),
        FocusMessageItem(id = "default_9", text = "Protect your focus.", isCustom = false),
        FocusMessageItem(id = "default_10", text = "You chose this goal.", isCustom = false)
    )

    val completionMessages: List<String> = listOf(
        "Session Completed!",
        "You did it!",
        "Well done!",
        "Another step forward.",
        "Great progress!"
    )

    const val MAX_MESSAGE_LENGTH = 80
}

enum class LockReminderStyle(
    val storageKey: String,
    val label: String
) {
    SOFT_BOX("soft_box", "Card Box"),
    PLAIN_TEXT("plain_text", "Minimal Text");

    companion object {
        fun fromStorageKey(key: String?): LockReminderStyle =
            entries.firstOrNull { it.storageKey == key } ?: SOFT_BOX
    }
}

object DefaultLockReminders {
    val presets: List<String> = listOf(
        "Discipline is choosing what you want most over what you want now.",
        "Every quiet hour now builds the future you actually respect.",
        "Don't trade your real goals for five minutes of mindless scrolling.",
        "Silence the noise. Finish what you promised yourself."
    )

    val DEFAULT_TEXT: String = presets.first()
    const val MAX_REMINDER_LENGTH = 120
}

data class MascotPreferences(
    val selectedMascot: MascotType = MascotType.DEFAULT,
    val focusMessagesEnabled: Boolean = true,
    val messageFrequency: FocusMessageFrequency = FocusMessageFrequency.EVERY_3_MIN,
    val reducedMotionEnabled: Boolean = false,
    val messages: List<FocusMessageItem> = DefaultFocusMessages.items,
    val bunnyPeekEntranceSeen: Boolean = false,
    val lockReminderEnabled: Boolean = true,
    val lockReminderText: String = DefaultLockReminders.DEFAULT_TEXT,
    val lockReminderStyle: LockReminderStyle = LockReminderStyle.SOFT_BOX
)

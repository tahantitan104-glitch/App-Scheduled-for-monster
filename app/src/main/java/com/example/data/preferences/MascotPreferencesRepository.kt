package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.DefaultFocusMessages
import com.example.domain.model.DefaultLockReminders
import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.FocusMessageItem
import com.example.domain.model.LockReminderStyle
import com.example.domain.model.MascotPreferences
import com.example.domain.model.MascotType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.mascotDataStore: DataStore<Preferences> by preferencesDataStore(name = "resolve_mascot_prefs")

class MascotPreferencesRepository(private val context: Context) {

    private object Keys {
        val SELECTED_MASCOT = stringPreferencesKey("selected_mascot")
        val FOCUS_MESSAGES_ENABLED = booleanPreferencesKey("focus_messages_enabled")
        val MESSAGE_FREQUENCY = stringPreferencesKey("message_frequency")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion_enabled")
        val FOCUS_MESSAGES_JSON = stringPreferencesKey("focus_messages_json")
        val BUNNY_PEEK_ENTRANCE_SEEN = booleanPreferencesKey("bunny_peek_entrance_seen")
        val LOCK_REMINDER_ENABLED = booleanPreferencesKey("lock_reminder_enabled")
        val LOCK_REMINDER_TEXT = stringPreferencesKey("lock_reminder_text")
        val LOCK_REMINDER_STYLE = stringPreferencesKey("lock_reminder_style")
    }

    val mascotPreferencesFlow: Flow<MascotPreferences> = context.mascotDataStore.data.map { prefs ->
        MascotPreferences(
            selectedMascot = MascotType.fromStorageKey(prefs[Keys.SELECTED_MASCOT]),
            focusMessagesEnabled = prefs[Keys.FOCUS_MESSAGES_ENABLED] ?: true,
            messageFrequency = FocusMessageFrequency.fromStorageKey(prefs[Keys.MESSAGE_FREQUENCY]),
            reducedMotionEnabled = prefs[Keys.REDUCED_MOTION] ?: false,
            messages = decodeMessages(prefs[Keys.FOCUS_MESSAGES_JSON]),
            bunnyPeekEntranceSeen = prefs[Keys.BUNNY_PEEK_ENTRANCE_SEEN] ?: false,
            lockReminderEnabled = prefs[Keys.LOCK_REMINDER_ENABLED] ?: true,
            lockReminderText = prefs[Keys.LOCK_REMINDER_TEXT]
                ?.takeIf { it.isNotBlank() }
                ?: DefaultLockReminders.DEFAULT_TEXT,
            lockReminderStyle = LockReminderStyle.fromStorageKey(prefs[Keys.LOCK_REMINDER_STYLE])
        )
    }

    suspend fun getPreferencesSnapshot(): MascotPreferences = mascotPreferencesFlow.first()

    suspend fun setSelectedMascot(mascot: MascotType) {
        context.mascotDataStore.edit { prefs ->
            prefs[Keys.SELECTED_MASCOT] = mascot.storageKey
        }
    }

    suspend fun setFocusMessagesEnabled(enabled: Boolean) {
        context.mascotDataStore.edit { prefs ->
            prefs[Keys.FOCUS_MESSAGES_ENABLED] = enabled
        }
    }

    suspend fun setMessageFrequency(frequency: FocusMessageFrequency) {
        context.mascotDataStore.edit { prefs ->
            prefs[Keys.MESSAGE_FREQUENCY] = frequency.storageKey
        }
    }

    suspend fun setReducedMotionEnabled(enabled: Boolean) {
        context.mascotDataStore.edit { prefs ->
            prefs[Keys.REDUCED_MOTION] = enabled
        }
    }

    suspend fun markBunnyPeekEntranceSeen(seen: Boolean = true) {
        context.mascotDataStore.edit { prefs ->
            prefs[Keys.BUNNY_PEEK_ENTRANCE_SEEN] = seen
        }
    }

    suspend fun saveMessages(messages: List<FocusMessageItem>) {
        context.mascotDataStore.edit { prefs ->
            prefs[Keys.FOCUS_MESSAGES_JSON] = encodeMessages(messages)
        }
    }

    suspend fun restoreDefaultMessages() {
        saveMessages(DefaultFocusMessages.items)
    }

    suspend fun setLockReminderEnabled(enabled: Boolean) {
        context.mascotDataStore.edit { prefs ->
            prefs[Keys.LOCK_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setLockReminderText(text: String) {
        val clean = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
            .take(DefaultLockReminders.MAX_REMINDER_LENGTH)
        if (clean.isEmpty()) return
        context.mascotDataStore.edit { prefs ->
            prefs[Keys.LOCK_REMINDER_TEXT] = clean
        }
    }

    suspend fun setLockReminderStyle(style: LockReminderStyle) {
        context.mascotDataStore.edit { prefs ->
            prefs[Keys.LOCK_REMINDER_STYLE] = style.storageKey
        }
    }

    internal companion object {
        fun encodeMessages(messages: List<FocusMessageItem>): String {
            val array = JSONArray()
            messages.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("isCustom", item.isCustom)
                }
                array.put(obj)
            }
            return array.toString()
        }

        fun decodeMessages(rawJson: String?): List<FocusMessageItem> {
            if (rawJson == null) return DefaultFocusMessages.items
            return runCatching {
                val array = JSONArray(rawJson)
                buildList(array.length()) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val id = obj.optString("id").trim()
                        val text = obj.optString("text").trim()
                        val isCustom = obj.optBoolean("isCustom", false)
                        if (id.isNotEmpty() && text.isNotEmpty()) {
                            add(FocusMessageItem(id = id, text = text, isCustom = isCustom))
                        }
                    }
                }
            }.getOrElse { DefaultFocusMessages.items }
        }
    }
}

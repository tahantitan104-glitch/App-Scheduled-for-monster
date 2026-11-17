package com.example.mascot

import com.example.data.preferences.MascotPreferencesRepository
import com.example.domain.model.DefaultFocusMessages
import com.example.domain.model.FocusMessageFrequency
import com.example.domain.model.FocusMessageItem
import java.util.UUID
import kotlin.random.Random

/**
 * Manages focus messages: default messages, custom message CRUD (add, edit, delete, restore),
 * sanitization, and non-repetitive selection for Lock Screen bubbles and completion celebrations.
 */
class FocusMessageManager(
    private val preferencesRepository: MascotPreferencesRepository,
    private val random: Random = Random.Default
) {

    fun sanitizeMessage(raw: String): String? {
        val collapsed = raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return null
        return collapsed.take(DefaultFocusMessages.MAX_MESSAGE_LENGTH)
    }

    fun selectNextFocusMessage(
        messages: List<FocusMessageItem>,
        lastSelectedId: String?
    ): FocusMessageItem? {
        val valid = messages.filter { it.text.isNotBlank() }
        if (valid.isEmpty()) return null
        if (valid.size == 1) return valid.first()

        val candidates = if (lastSelectedId != null) {
            valid.filter { it.id != lastSelectedId }.ifEmpty { valid }
        } else {
            valid
        }
        return candidates[random.nextInt(candidates.size)]
    }

    fun selectCompletionMessage(lastCompletionMessage: String? = null): String {
        val all = DefaultFocusMessages.completionMessages
        val candidates = if (lastCompletionMessage != null && all.size > 1) {
            all.filter { it != lastCompletionMessage }.ifEmpty { all }
        } else {
            all
        }
        return candidates[random.nextInt(candidates.size)]
    }

    suspend fun setMessagesEnabled(enabled: Boolean) {
        preferencesRepository.setFocusMessagesEnabled(enabled)
    }

    suspend fun setFrequency(frequency: FocusMessageFrequency) {
        preferencesRepository.setMessageFrequency(frequency)
    }

    suspend fun addCustomMessage(rawText: String): Boolean {
        val clean = sanitizeMessage(rawText) ?: return false
        val current = preferencesRepository.getPreferencesSnapshot().messages
        val created = FocusMessageItem(
            id = "custom_${UUID.randomUUID()}",
            text = clean,
            isCustom = true
        )
        preferencesRepository.saveMessages(current + created)
        return true
    }

    suspend fun editMessage(messageId: String, rawText: String): Boolean {
        val clean = sanitizeMessage(rawText) ?: return false
        val current = preferencesRepository.getPreferencesSnapshot().messages
        var updatedAny = false
        val updatedList = current.map { item ->
            if (item.id == messageId) {
                updatedAny = true
                item.copy(text = clean)
            } else {
                item
            }
        }
        if (!updatedAny) return false
        preferencesRepository.saveMessages(updatedList)
        return true
    }

    suspend fun deleteMessage(messageId: String) {
        val current = preferencesRepository.getPreferencesSnapshot().messages
        val filtered = current.filterNot { it.id == messageId }
        preferencesRepository.saveMessages(filtered)
    }

    suspend fun restoreDefaultMessages() {
        preferencesRepository.restoreDefaultMessages()
    }
}

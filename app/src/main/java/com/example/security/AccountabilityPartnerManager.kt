package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.preferences.StoredPartnerPinRecord
import com.example.domain.model.AccountabilityPartnerConfig
import com.example.domain.model.AppLockVerificationResult
import com.example.domain.time.ClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Manages the Accountability Partner PIN using salted PBKDF2-SHA256 + Keystore wrapping on API 23+
 * with exponential backoff throttling. Plaintext partner PINs are never persisted.
 */
class AccountabilityPartnerManager(
    private val preferencesRepository: ResolvePreferencesRepository,
    private val clockProvider: ClockProvider
) {
    private val secureRandom = SecureRandom()

    val configFlow: Flow<AccountabilityPartnerConfig> = preferencesRepository.accountabilityPartnerConfigFlow

    fun isValidPinFormat(rawPin: String): Boolean {
        val clean = rawPin.trim()
        return clean.length in 4..8 && clean.all { it.isDigit() }
    }

    suspend fun savePartnerSetup(
        enabled: Boolean,
        partnerName: String,
        newPinOrNull: String?,
        requireForStrictUnlock: Boolean,
        requireForQuotaOverride: Boolean
    ): Boolean = withContext(Dispatchers.Default) {
        if (newPinOrNull != null) {
            val cleanPin = newPinOrNull.trim()
            if (!isValidPinFormat(cleanPin)) return@withContext false
            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val derived = deriveKey("partner:$cleanPin", salt)
            val wrapped = wrapWithKeystore(derived)
            val record = if (wrapped != null) {
                StoredPartnerPinRecord(
                    verifierBase64 = Base64.encodeToString(wrapped.first, Base64.NO_WRAP),
                    saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                    ivBase64 = Base64.encodeToString(wrapped.second, Base64.NO_WRAP),
                    isKeystoreWrapped = true
                )
            } else {
                StoredPartnerPinRecord(
                    verifierBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
                    saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                    ivBase64 = null,
                    isKeystoreWrapped = false
                )
            }
            preferencesRepository.savePartnerPinRecord(record)
        }

        preferencesRepository.saveAccountabilityPartnerMetadata(
            enabled = enabled,
            partnerName = partnerName.trim().take(48),
            requireForStrictUnlock = requireForStrictUnlock,
            requireForQuotaOverride = requireForQuotaOverride
        )
        true
    }

    suspend fun clearPartnerPin() {
        preferencesRepository.clearPartnerPinRecord()
    }

    suspend fun verifyPartnerPin(rawInput: String): AppLockVerificationResult =
        withContext(Dispatchers.Default) {
            val nowWallMs = clockProvider.wallClockMs()
            val throttle = preferencesRepository.getPartnerPinThrottleState()
            if (throttle.lockoutUntilWallMs > nowWallMs) {
                val remainingSec = ((throttle.lockoutUntilWallMs - nowWallMs + 999L) / 1000L)
                    .coerceAtLeast(1L)
                return@withContext AppLockVerificationResult.Throttled(remainingSec)
            }

            val record = preferencesRepository.getPartnerPinRecord()
                ?: return@withContext AppLockVerificationResult.NotConfigured

            val clean = rawInput.trim()
            if (!isValidPinFormat(clean)) {
                return@withContext recordFailure(throttle.failedAttempts, nowWallMs)
            }

            val salt = Base64.decode(record.saltBase64, Base64.NO_WRAP)
            val storedPayload = Base64.decode(record.verifierBase64, Base64.NO_WRAP)
            val expectedDerived = if (record.isKeystoreWrapped) {
                val iv = record.ivBase64?.let { Base64.decode(it, Base64.NO_WRAP) }
                    ?: return@withContext recordFailure(throttle.failedAttempts, nowWallMs)
                unwrapWithKeystore(storedPayload, iv)
                    ?: return@withContext recordFailure(throttle.failedAttempts, nowWallMs)
            } else {
                storedPayload
            }

            val candidateDerived = deriveKey("partner:$clean", salt)
            val matches = MessageDigest.isEqual(expectedDerived, candidateDerived)
            if (matches) {
                preferencesRepository.updatePartnerPinThrottleState(failedAttempts = 0, lockoutUntilWallMs = 0L)
                AppLockVerificationResult.Verified
            } else {
                recordFailure(throttle.failedAttempts, nowWallMs)
            }
        }

    private suspend fun recordFailure(previousAttempts: Int, nowWallMs: Long): AppLockVerificationResult {
        val nextAttempts = previousAttempts + 1
        val remainder = nextAttempts % MAX_ATTEMPTS_BEFORE_COOLDOWN
        return if (nextAttempts >= MAX_ATTEMPTS_BEFORE_COOLDOWN && remainder == 0) {
            val tier = (nextAttempts / MAX_ATTEMPTS_BEFORE_COOLDOWN).coerceAtMost(4)
            val cooldownMs = BASE_COOLDOWN_MS * (1L shl (tier - 1))
            preferencesRepository.updatePartnerPinThrottleState(nextAttempts, nowWallMs + cooldownMs)
            AppLockVerificationResult.Throttled((cooldownMs / 1000L).coerceAtLeast(1L))
        } else {
            preferencesRepository.updatePartnerPinThrottleState(nextAttempts, 0L)
            val remaining = MAX_ATTEMPTS_BEFORE_COOLDOWN - remainder
            AppLockVerificationResult.InvalidCredential(remaining)
        }
    }

    private fun deriveKey(material: String, salt: ByteArray): ByteArray {
        val chars = material.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            } catch (_: NoSuchAlgorithmException) {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            }
            factory.generateSecret(spec).encoded
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun wrapWithKeystore(plaintext: ByteArray): Pair<ByteArray, ByteArray>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return runCatching {
            val key = Api23KeystoreHelper.getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.doFinal(plaintext) to cipher.iv
        }.getOrNull()
    }

    private fun unwrapWithKeystore(ciphertext: ByteArray, iv: ByteArray): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return runCatching {
            val key = Api23KeystoreHelper.getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private object Api23KeystoreHelper {
        fun getOrCreateKeystoreKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            return generator.generateKey()
        }
    }

    companion object {
        private const val SALT_BYTES = 16
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256
        private const val MAX_ATTEMPTS_BEFORE_COOLDOWN = 5
        private const val BASE_COOLDOWN_MS = 30_000L
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "resolve_partner_pin_verifier_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

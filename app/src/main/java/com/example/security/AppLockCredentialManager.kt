package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.preferences.StoredAppLockCredentialRecord
import com.example.domain.model.AppLockAuthMethod
import com.example.domain.model.AppLockPinLength
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
 * Stores and verifies App Lock credentials using salted PBKDF2 + AndroidKeyStore AES-GCM
 * wrapping. Plaintext PINs, patterns, or 6-cell tap sequences are never persisted.
 */
class AppLockCredentialManager(
    private val preferencesRepository: ResolvePreferencesRepository,
    private val clockProvider: ClockProvider
) {
    private val secureRandom = SecureRandom()

    val appLockEnabledFlow: Flow<Boolean> = preferencesRepository.appLockEnabledFlow
    val hasCredentialFlow: Flow<Boolean> = preferencesRepository.hasAppLockCredentialFlow
    val authMethodFlow: Flow<AppLockAuthMethod> = preferencesRepository.appLockMethodFlow
    val pinLengthFlow: Flow<AppLockPinLength> = preferencesRepository.appLockPinLengthFlow

    fun isValidCredentialFormat(
        method: AppLockAuthMethod,
        pinLength: AppLockPinLength,
        rawCredential: String
    ): Boolean {
        return when (method) {
            AppLockAuthMethod.PIN -> {
                rawCredential.length == pinLength.digits && rawCredential.all { it.isDigit() }
            }
            AppLockAuthMethod.PATTERN -> {
                val nodes = parseIndexSequence(rawCredential) ?: return false
                nodes.size >= MIN_PATTERN_NODES &&
                    nodes.size <= 9 &&
                    nodes.all { it in 0..8 } &&
                    nodes.toSet().size == nodes.size
            }
            AppLockAuthMethod.SIX_CELL_SEQUENTIAL -> {
                val cells = parseIndexSequence(rawCredential) ?: return false
                cells.size >= MIN_SIX_CELL_SEQUENCE &&
                    cells.size <= 6 &&
                    cells.all { it in 0..5 } &&
                    cells.toSet().size == cells.size
            }
        }
    }

    suspend fun saveCredential(
        method: AppLockAuthMethod,
        pinLength: AppLockPinLength,
        rawCredential: String
    ): Boolean = withContext(Dispatchers.Default) {
        val normalized = normalizeCredential(method, rawCredential)
        if (!isValidCredentialFormat(method, pinLength, normalized)) {
            return@withContext false
        }

        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val derived = deriveKey("${method.name}:${pinLength.digits}:$normalized", salt)

        val wrapped = wrapWithKeystore(derived)
        val record = if (wrapped != null) {
            StoredAppLockCredentialRecord(
                method = method,
                pinLength = pinLength,
                verifierBase64 = Base64.encodeToString(wrapped.first, Base64.NO_WRAP),
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(wrapped.second, Base64.NO_WRAP),
                isKeystoreWrapped = true
            )
        } else {
            StoredAppLockCredentialRecord(
                method = method,
                pinLength = pinLength,
                verifierBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                ivBase64 = null,
                isKeystoreWrapped = false
            )
        }

        preferencesRepository.saveAppLockCredentialRecord(record)
        true
    }

    suspend fun verifyCredential(rawInput: String): AppLockVerificationResult =
        withContext(Dispatchers.Default) {
            val nowWallMs = clockProvider.wallClockMs()
            val throttle = preferencesRepository.getAppLockThrottleState()
            if (throttle.lockoutUntilWallMs > nowWallMs) {
                val remainingSec = ((throttle.lockoutUntilWallMs - nowWallMs + 999L) / 1000L)
                    .coerceAtLeast(1L)
                return@withContext AppLockVerificationResult.Throttled(remainingSec)
            }

            val record = preferencesRepository.getAppLockCredentialRecord()
                ?: return@withContext AppLockVerificationResult.NotConfigured

            val normalized = normalizeCredential(record.method, rawInput)
            if (!isValidCredentialFormat(record.method, record.pinLength, normalized)) {
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

            val candidateDerived = deriveKey(
                "${record.method.name}:${record.pinLength.digits}:$normalized",
                salt
            )
            val matches = MessageDigest.isEqual(expectedDerived, candidateDerived)

            if (matches) {
                preferencesRepository.updateAppLockThrottleState(failedAttempts = 0, lockoutUntilWallMs = 0L)
                AppLockVerificationResult.Verified
            } else {
                recordFailure(throttle.failedAttempts, nowWallMs)
            }
        }

    suspend fun getConfiguredRecord(): StoredAppLockCredentialRecord? =
        preferencesRepository.getAppLockCredentialRecord()

    suspend fun setAppLockEnabled(enabled: Boolean) {
        preferencesRepository.setAppLockEnabled(enabled)
    }

    suspend fun isAppLockEnabledSnapshot(): Boolean =
        preferencesRepository.isAppLockEnabledSnapshot()

    private suspend fun recordFailure(previousAttempts: Int, nowWallMs: Long): AppLockVerificationResult {
        val nextAttempts = previousAttempts + 1
        val remainder = nextAttempts % MAX_ATTEMPTS_BEFORE_COOLDOWN
        return if (nextAttempts >= MAX_ATTEMPTS_BEFORE_COOLDOWN && remainder == 0) {
            val tier = (nextAttempts / MAX_ATTEMPTS_BEFORE_COOLDOWN).coerceAtMost(4)
            val cooldownMs = BASE_COOLDOWN_MS * (1L shl (tier - 1))
            preferencesRepository.updateAppLockThrottleState(nextAttempts, nowWallMs + cooldownMs)
            AppLockVerificationResult.Throttled((cooldownMs / 1000L).coerceAtLeast(1L))
        } else {
            preferencesRepository.updateAppLockThrottleState(nextAttempts, 0L)
            val remaining = MAX_ATTEMPTS_BEFORE_COOLDOWN - remainder
            AppLockVerificationResult.InvalidCredential(remaining)
        }
    }

    private fun normalizeCredential(method: AppLockAuthMethod, raw: String): String {
        return when (method) {
            AppLockAuthMethod.PIN -> raw.trim()
            AppLockAuthMethod.PATTERN,
            AppLockAuthMethod.SIX_CELL_SEQUENTIAL -> {
                parseIndexSequence(raw)?.joinToString("-") ?: ""
            }
        }
    }

    private fun parseIndexSequence(raw: String): List<Int>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split("-", ",", " ").filter { it.isNotBlank() }
        val result = ArrayList<Int>(parts.size)
        for (p in parts) {
            val idx = p.toIntOrNull() ?: return null
            result.add(idx)
        }
        return result
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
            val ciphertext = cipher.doFinal(plaintext)
            ciphertext to cipher.iv
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
        const val MIN_PATTERN_NODES = 4
        const val MIN_SIX_CELL_SEQUENCE = 4
        private const val SALT_BYTES = 16
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256
        private const val MAX_ATTEMPTS_BEFORE_COOLDOWN = 5
        private const val BASE_COOLDOWN_MS = 30_000L
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "resolve_applock_verifier_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        fun formatSequence(indices: List<Int>): String = indices.joinToString("-")
    }
}

package com.example.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import com.example.data.preferences.ResolvePreferencesRepository
import com.example.data.preferences.StoredRecoveryRecord
import com.example.domain.model.RecoveryVerificationResult
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

class RecoveryCodeManager(
    private val preferencesRepository: ResolvePreferencesRepository,
    private val clockProvider: ClockProvider
) {
    private val secureRandom = SecureRandom()

    val hasRecoveryCodeFlow: Flow<Boolean> = preferencesRepository.hasRecoveryCodeFlow
    val recoveryCodeLengthFlow: Flow<Int> = preferencesRepository.recoveryCodeLengthFlow

    fun generateNumericCode(length: Int = DEFAULT_LENGTH): String {
        val validLength = if (length == EXTENDED_LENGTH) EXTENDED_LENGTH else DEFAULT_LENGTH
        val builder = StringBuilder(validLength)
        repeat(validLength) {
            builder.append(secureRandom.nextInt(10))
        }
        return builder.toString()
    }

    fun isValidCodeFormat(code: String, expectedLength: Int? = null): Boolean {
        if (!code.all { it.isDigit() }) return false
        return if (expectedLength != null) {
            code.length == expectedLength
        } else {
            code.length == DEFAULT_LENGTH || code.length == EXTENDED_LENGTH
        }
    }

    suspend fun saveRecoveryCode(rawCode: String): Boolean = withContext(Dispatchers.Default) {
        if (!isValidCodeFormat(rawCode)) return@withContext false

        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val derived = deriveKey(rawCode, salt)

        val wrapped = wrapWithKeystore(derived)
        val record = if (wrapped != null) {
            StoredRecoveryRecord(
                verifierBase64 = Base64.encodeToString(wrapped.first, Base64.NO_WRAP),
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(wrapped.second, Base64.NO_WRAP),
                isKeystoreWrapped = true,
                codeLength = rawCode.length
            )
        } else {
            StoredRecoveryRecord(
                verifierBase64 = Base64.encodeToString(derived, Base64.NO_WRAP),
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                ivBase64 = null,
                isKeystoreWrapped = false,
                codeLength = rawCode.length
            )
        }

        preferencesRepository.saveRecoveryRecord(record)
        true
    }

    suspend fun verifyRecoveryCode(rawInput: String): RecoveryVerificationResult =
        withContext(Dispatchers.Default) {
            val nowWallMs = clockProvider.wallClockMs()
            val throttle = preferencesRepository.getThrottleState()
            if (throttle.lockoutUntilWallMs > nowWallMs) {
                val remainingSec = ((throttle.lockoutUntilWallMs - nowWallMs + 999L) / 1000L).coerceAtLeast(1L)
                return@withContext RecoveryVerificationResult.Throttled(remainingSec)
            }

            val record = preferencesRepository.getRecoveryRecord()
                ?: return@withContext RecoveryVerificationResult.NotConfigured

            if (!rawInput.all { it.isDigit() } || rawInput.length != record.codeLength) {
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

            val candidateDerived = deriveKey(rawInput, salt)
            val matches = MessageDigest.isEqual(expectedDerived, candidateDerived)

            if (matches) {
                preferencesRepository.updateThrottleState(failedAttempts = 0, lockoutUntilWallMs = 0L)
                RecoveryVerificationResult.Verified
            } else {
                recordFailure(throttle.failedAttempts, nowWallMs)
            }
        }

    private suspend fun recordFailure(previousAttempts: Int, nowWallMs: Long): RecoveryVerificationResult {
        val nextAttempts = previousAttempts + 1
        val remainder = nextAttempts % MAX_ATTEMPTS_BEFORE_COOLDOWN
        return if (nextAttempts >= MAX_ATTEMPTS_BEFORE_COOLDOWN && remainder == 0) {
            val tier = (nextAttempts / MAX_ATTEMPTS_BEFORE_COOLDOWN).coerceAtMost(4)
            val cooldownMs = BASE_COOLDOWN_MS * (1L shl (tier - 1))
            preferencesRepository.updateThrottleState(nextAttempts, nowWallMs + cooldownMs)
            RecoveryVerificationResult.Throttled((cooldownMs / 1000L).coerceAtLeast(1L))
        } else {
            preferencesRepository.updateThrottleState(nextAttempts, 0L)
            val remaining = MAX_ATTEMPTS_BEFORE_COOLDOWN - remainder
            RecoveryVerificationResult.InvalidCode(remaining)
        }
    }

    private fun deriveKey(code: String, salt: ByteArray): ByteArray {
        val chars = code.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            } catch (_: NoSuchAlgorithmException) {
                // Android 5-7 (API 21-25) only ships PBKDF2WithHmacSHA1 in default provider
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
        const val DEFAULT_LENGTH = 16
        const val EXTENDED_LENGTH = 32
        private const val SALT_BYTES = 16
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val MAX_ATTEMPTS_BEFORE_COOLDOWN = 5
        private const val BASE_COOLDOWN_MS = 30_000L
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "resolve_recovery_verifier_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        fun formatGrouped(code: String): String =
            code.chunked(4).joinToString(" ")
    }
}

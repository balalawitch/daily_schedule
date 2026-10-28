package love.panyi.forever.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiEngine(val label: String) {
    NANO("Gemini Nano"),
    GEMMA("本地 Gemma"),
    CLOUD("云端 API"),
}

data class CloudConfig(val url: String = "", val apiKey: String = "", val model: String = "") {
    /** 三项都已填写，且地址是 https://。 */
    val isComplete: Boolean
        get() = url.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank() && url.trim().startsWith("https://")
}

/** 给用户看的错误（网络、HTTP）。 */
class AiException(message: String) : Exception(message)

interface AiSettings {
    val engine: AiEngine
    fun selectEngine(e: AiEngine)
    fun cloudConfig(): CloudConfig
    fun saveCloudConfig(c: CloudConfig)
}

/** SharedPreferences（`ai_settings`）实现。API Key 用 Android Keystore 的 AES/GCM 加密后才落盘。 */
class AiSettingsStore(context: Context) : AiSettings {
    private val prefs = context.applicationContext.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    override val engine: AiEngine
        get() = prefs.getString(KEY_ENGINE, null)?.let { name -> AiEngine.entries.firstOrNull { it.name == name } }
            ?: AiEngine.CLOUD

    override fun selectEngine(e: AiEngine) {
        prefs.edit().putString(KEY_ENGINE, e.name).apply()
    }

    override fun cloudConfig(): CloudConfig = CloudConfig(
        url = prefs.getString(KEY_URL, "").orEmpty(),
        apiKey = decrypt(prefs.getString(KEY_KEY, null)),
        model = prefs.getString(KEY_MODEL, "").orEmpty(),
    )

    override fun saveCloudConfig(c: CloudConfig) {
        val key = c.apiKey.trim()
        // 先加密再写；加密失败时不写入任何明文，也不保留旧密文。
        val encrypted = if (key.isEmpty()) null else encrypt(key)
        prefs.edit().apply {
            putString(KEY_URL, c.url.trim())
            putString(KEY_MODEL, c.model.trim())
            if (encrypted == null) remove(KEY_KEY) else putString(KEY_KEY, encrypted)
        }.apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Base64(iv + ciphertext)；Keystore 不可用时返回 null（Key 不会被保存）。 */
    private fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    /** 解密失败（例如密钥被系统清掉、备份恢复到新设备）当作空 Key。 */
    private fun decrypt(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, GCM_IV_BYTES))
            String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private companion object {
        const val KEY_ENGINE = "engine"
        const val KEY_URL = "cloud_url"
        const val KEY_MODEL = "cloud_model"
        const val KEY_KEY = "cloud_key"
        const val KEY_ALIAS = "ai_api_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
    }
}

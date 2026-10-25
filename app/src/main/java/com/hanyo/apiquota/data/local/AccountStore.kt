package com.hanyo.apiquota.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hanyo.apiquota.data.model.Account
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * 账号（含 API Key）本地加密存储。使用 EncryptedSharedPreferences，密钥存于 Android Keystore。
 * 所有数据仅保存在本机，不会上传。
 */
class AccountStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ai_quota_accounts",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getAccounts(): List<Account> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Account>>(raw) }
            .getOrDefault(emptyList())
    }

    fun saveAll(list: List<Account>) {
        prefs.edit().putString(KEY, json.encodeToString(list)).apply()
    }

    fun add(acc: Account) = saveAll(getAccounts() + acc)
    fun remove(id: String) = saveAll(getAccounts().filter { it.id != id })
    fun update(acc: Account) = saveAll(getAccounts().map { if (it.id == acc.id) acc else it })

    companion object {
        private const val KEY = "accounts"
    }
}

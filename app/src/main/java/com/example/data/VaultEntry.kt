/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 VaultKeep Contributors
 */

package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class EntryType {
    PASSWORD, // Standard Login
    PASSKEY,
    SECURE_NOTE,
    CREDIT_CARD,
    IDENTITY
}

enum class CustomFieldType {
    PLAIN_TEXT,
    SECRET,
    URL,
    DATE
}

data class CustomField(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val value: String = "",
    val fieldType: CustomFieldType = CustomFieldType.PLAIN_TEXT
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("value", value)
        put("fieldType", fieldType.name)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): CustomField {
            val typeStr = json.optString("fieldType", CustomFieldType.PLAIN_TEXT.name)
            val parsedType = try {
                CustomFieldType.valueOf(typeStr)
            } catch (_: Exception) {
                CustomFieldType.PLAIN_TEXT
            }
            return CustomField(
                id = json.optString("id", UUID.randomUUID().toString()),
                label = json.optString("label", ""),
                value = json.optString("value", ""),
                fieldType = parsedType
            )
        }
    }
}

data class PasswordHistoryItem(
    val password: String,
    val changedAt: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("password", password)
        put("changedAt", changedAt)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): PasswordHistoryItem {
            return PasswordHistoryItem(
                password = json.optString("password", ""),
                changedAt = json.optLong("changedAt", System.currentTimeMillis())
            )
        }
    }
}

data class EncryptedAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val fileSize: Long,
    val dataBase64: String, // Encrypted along with vault container
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("fileName", fileName)
        put("mimeType", mimeType)
        put("fileSize", fileSize)
        put("dataBase64", dataBase64)
        put("createdAt", createdAt)
    }

    companion object {
        const val MAX_ATTACHMENT_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB per attachment
        const val MAX_VAULT_ATTACHMENTS_TOTAL_BYTES = 10 * 1024 * 1024L // 10 MB per vault

        fun fromJsonObject(json: JSONObject): EncryptedAttachment {
            return EncryptedAttachment(
                id = json.optString("id", UUID.randomUUID().toString()),
                fileName = json.optString("fileName", "attachment"),
                mimeType = json.optString("mimeType", "application/octet-stream"),
                fileSize = json.optLong("fileSize", 0L),
                dataBase64 = json.optString("dataBase64", ""),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class VaultEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String, // Website or app name / Note title / Card name
    val username: String = "", // Username or email
    val password: String = "", // Password
    val notes: String = "", // Multi-line encrypted notes or body
    val folder: String = "", // Folder name
    val tags: List<String> = emptyList(), // Tags list
    val isFavorite: Boolean = false,
    val totpSecret: String = "", // RFC 6238 TOTP authenticator secret (Base32)
    val totpPeriod: Int = 30, // Period in seconds
    val totpDigits: Int = 6, // 6 or 8 digits
    val totpAlgorithm: String = "SHA1",
    val url: String = "", // Website domain/url
    val packageName: String = "", // Android package
    val entryType: EntryType = EntryType.PASSWORD,

    // Credit Card Template Fields
    val cardholderName: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val cardPin: String = "",

    // Identity Template Fields
    val identityName: String = "",
    val identityAddress: String = "",
    val identityIdNumber: String = "",
    val identityPhone: String = "",
    val identityEmail: String = "",

    // Custom Fields
    val customFields: List<CustomField> = emptyList(),

    // Password History (last 5 previous passwords)
    val passwordHistory: List<PasswordHistoryItem> = emptyList(),

    // Encrypted Attachments
    val attachments: List<EncryptedAttachment> = emptyList(),

    // Passkey metadata
    val passkeyCredentialId: String = "", // Base64Url
    val passkeyRpId: String = "",
    val passkeyUserHandle: String = "", // Base64Url
    val passkeyPrivateKeyPkcs8: String = "", // Base64 PKCS#8 private key
    val passkeyPublicKeyCose: String = "", // Base64 COSE public key
    val passkeySignCount: Int = 0,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0L
) {
    val isPasskey: Boolean
        get() = entryType == EntryType.PASSKEY

    val isSecureNote: Boolean
        get() = entryType == EntryType.SECURE_NOTE

    val isCreditCard: Boolean
        get() = entryType == EntryType.CREDIT_CARD

    val isIdentity: Boolean
        get() = entryType == EntryType.IDENTITY

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("username", username)
            put("password", password)
            put("notes", notes)
            put("folder", folder)
            val tagsArr = JSONArray()
            tags.forEach { tagsArr.put(it) }
            put("tags", tagsArr)
            put("isFavorite", isFavorite)
            put("totpSecret", totpSecret)
            put("totpPeriod", totpPeriod)
            put("totpDigits", totpDigits)
            put("totpAlgorithm", totpAlgorithm)
            put("url", url)
            put("packageName", packageName)
            put("entryType", entryType.name)

            put("cardholderName", cardholderName)
            put("cardNumber", cardNumber)
            put("cardExpiry", cardExpiry)
            put("cardCvv", cardCvv)
            put("cardPin", cardPin)

            put("identityName", identityName)
            put("identityAddress", identityAddress)
            put("identityIdNumber", identityIdNumber)
            put("identityPhone", identityPhone)
            put("identityEmail", identityEmail)

            val customFieldsArr = JSONArray()
            customFields.forEach { customFieldsArr.put(it.toJsonObject()) }
            put("customFields", customFieldsArr)

            val historyArr = JSONArray()
            passwordHistory.forEach { historyArr.put(it.toJsonObject()) }
            put("passwordHistory", historyArr)

            val attachArr = JSONArray()
            attachments.forEach { attachArr.put(it.toJsonObject()) }
            put("attachments", attachArr)

            put("passkeyCredentialId", passkeyCredentialId)
            put("passkeyRpId", passkeyRpId)
            put("passkeyUserHandle", passkeyUserHandle)
            put("passkeyPrivateKeyPkcs8", passkeyPrivateKeyPkcs8)
            put("passkeyPublicKeyCose", passkeyPublicKeyCose)
            put("passkeySignCount", passkeySignCount)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("isDeleted", isDeleted)
            put("deletedAt", deletedAt)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): VaultEntry {
            val typeStr = json.optString("entryType", EntryType.PASSWORD.name)
            val parsedType = try {
                EntryType.valueOf(typeStr)
            } catch (_: Exception) {
                EntryType.PASSWORD
            }

            val tagsList = mutableListOf<String>()
            val tagsArr = json.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    tagsList.add(tagsArr.getString(i))
                }
            }

            val customFieldsList = mutableListOf<CustomField>()
            val customFieldsArr = json.optJSONArray("customFields")
            if (customFieldsArr != null) {
                for (i in 0 until customFieldsArr.length()) {
                    customFieldsList.add(CustomField.fromJsonObject(customFieldsArr.getJSONObject(i)))
                }
            }

            val historyList = mutableListOf<PasswordHistoryItem>()
            val historyArr = json.optJSONArray("passwordHistory")
            if (historyArr != null) {
                for (i in 0 until historyArr.length()) {
                    historyList.add(PasswordHistoryItem.fromJsonObject(historyArr.getJSONObject(i)))
                }
            }

            val attachList = mutableListOf<EncryptedAttachment>()
            val attachArr = json.optJSONArray("attachments")
            if (attachArr != null) {
                for (i in 0 until attachArr.length()) {
                    attachList.add(EncryptedAttachment.fromJsonObject(attachArr.getJSONObject(i)))
                }
            }

            return VaultEntry(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", ""),
                username = json.optString("username", ""),
                password = json.optString("password", ""),
                notes = json.optString("notes", ""),
                folder = json.optString("folder", ""),
                tags = tagsList,
                isFavorite = json.optBoolean("isFavorite", false),
                totpSecret = json.optString("totpSecret", ""),
                totpPeriod = json.optInt("totpPeriod", 30),
                totpDigits = json.optInt("totpDigits", 6),
                totpAlgorithm = json.optString("totpAlgorithm", "SHA1"),
                url = json.optString("url", ""),
                packageName = json.optString("packageName", ""),
                entryType = parsedType,
                cardholderName = json.optString("cardholderName", ""),
                cardNumber = json.optString("cardNumber", ""),
                cardExpiry = json.optString("cardExpiry", ""),
                cardCvv = json.optString("cardCvv", ""),
                cardPin = json.optString("cardPin", ""),
                identityName = json.optString("identityName", ""),
                identityAddress = json.optString("identityAddress", ""),
                identityIdNumber = json.optString("identityIdNumber", ""),
                identityPhone = json.optString("identityPhone", ""),
                identityEmail = json.optString("identityEmail", ""),
                customFields = customFieldsList,
                passwordHistory = historyList,
                attachments = attachList,
                passkeyCredentialId = json.optString("passkeyCredentialId", ""),
                passkeyRpId = json.optString("passkeyRpId", ""),
                passkeyUserHandle = json.optString("passkeyUserHandle", ""),
                passkeyPrivateKeyPkcs8 = json.optString("passkeyPrivateKeyPkcs8", ""),
                passkeyPublicKeyCose = json.optString("passkeyPublicKeyCose", ""),
                passkeySignCount = json.optInt("passkeySignCount", 0),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                isDeleted = json.optBoolean("isDeleted", false),
                deletedAt = json.optLong("deletedAt", 0L)
            )
        }
    }
}

data class VaultPayload(
    val version: Int = 3,
    val entries: List<VaultEntry> = emptyList(),
    val folders: List<String> = emptyList(),
    val tags: List<String> = emptyList()
) {
    fun toJsonBytes(): ByteArray {
        val root = JSONObject()
        root.put("version", version)
        val array = JSONArray()
        for (entry in entries) {
            array.put(entry.toJsonObject())
        }
        root.put("entries", array)
        val foldersArray = JSONArray()
        for (f in folders) {
            foldersArray.put(f)
        }
        root.put("folders", foldersArray)
        val tagsArray = JSONArray()
        for (t in tags) {
            tagsArray.put(t)
        }
        root.put("tags", tagsArray)
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromJsonBytes(bytes: ByteArray): VaultPayload {
            val jsonString = String(bytes, Charsets.UTF_8)
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 3)
            val array = root.optJSONArray("entries") ?: JSONArray()
            val list = mutableListOf<VaultEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(VaultEntry.fromJsonObject(obj))
            }
            val foldersArray = root.optJSONArray("folders") ?: JSONArray()
            val foldersList = mutableListOf<String>()
            for (i in 0 until foldersArray.length()) {
                foldersList.add(foldersArray.getString(i))
            }
            val tagsArray = root.optJSONArray("tags") ?: JSONArray()
            val tagsList = mutableListOf<String>()
            for (i in 0 until tagsArray.length()) {
                tagsList.add(tagsArray.getString(i))
            }
            return VaultPayload(version = version, entries = list, folders = foldersList, tags = tagsList)
        }
    }
}

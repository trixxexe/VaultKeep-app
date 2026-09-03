/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 VaultKeep Contributors
 */

package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VaultExporter {

    /**
     * Exports entries as standard RFC-4180 CSV (compatible with Chrome, Bitwarden, KeePass).
     */
    fun exportToCsv(entries: List<VaultEntry>, outputStream: OutputStream) {
        val writer = outputStream.bufferedWriter(Charsets.UTF_8)
        writer.use { out ->
            // Header line
            out.write("folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\r\n")

            for (entry in entries) {
                if (entry.isDeleted) continue

                val folder = escapeCsv(entry.folder)
                val favorite = if (entry.isFavorite) "1" else "0"
                val type = escapeCsv(entry.entryType.name.lowercase(Locale.ROOT))
                val name = escapeCsv(entry.title)
                val notes = escapeCsv(entry.notes)

                // Custom fields summary
                val fieldsSummary = if (entry.customFields.isNotEmpty()) {
                    entry.customFields.joinToString("; ") { "${it.label}=${it.value}" }
                } else ""
                val fields = escapeCsv(fieldsSummary)

                val reprompt = "0"
                val uri = escapeCsv(entry.url)
                val username = escapeCsv(entry.username)
                val password = escapeCsv(entry.password)
                val totp = escapeCsv(entry.totpSecret)

                out.write("$folder,$favorite,$type,$name,$notes,$fields,$reprompt,$uri,$username,$password,$totp\r\n")
            }
            out.flush()
        }
    }

    /**
     * Exports entries as clean JSON.
     */
    fun exportToJson(entries: List<VaultEntry>, outputStream: OutputStream) {
        val writer = outputStream.bufferedWriter(Charsets.UTF_8)
        writer.use { out ->
            val root = JSONObject()
            val array = JSONArray()
            for (entry in entries) {
                if (entry.isDeleted) continue
                array.put(entry.toJsonObject())
            }
            root.put("encrypted", false)
            root.put("exportedAt", System.currentTimeMillis())
            root.put("items", array)
            out.write(root.toString(2))
            out.flush()
        }
    }

    /**
     * Generates a printable / shareable Emergency Recovery Kit text sheet.
     */
    fun generateEmergencyRecoveryKit(
        context: Context,
        entriesCount: Int,
        foldersCount: Int,
        vaultHint: String
    ): String {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        val dateString = dateFormat.format(Date())

        return """
======================================================================
               VAULTKEEP EMERGENCY RECOVERY SHEET
======================================================================
Generated On: $dateString
Storage Model: Zero-Knowledge Client-Side Encrypted (Argon2id + AES-256-GCM)
Total Active Items: $entriesCount credentials
Folders: $foldersCount collections

----------------------------------------------------------------------
IMPORTANT ZERO-KNOWLEDGE NOTICE:
VaultKeep employs cryptographic zero-knowledge architecture. No servers,
developers, or third parties possess your master password or encryption key.
If you lose your master password, your vault CANNOT be recovered.
Keep this sheet stored in a fireproof safe, safety deposit box, or
with a trusted emergency contact.
----------------------------------------------------------------------

1. MASTER PASSWORD HINT / LOCATION NOTE:
   Hint: ${vaultHint.ifBlank { "(No hint configured)" }}
   
2. PHYSICAL MASTER PASSWORD RECORD (WRITE DOWN BY HAND):
   [                                                                ]
   [                                                                ]

3. VAULT RECOVERY INSTRUCTIONS:
   a. Install VaultKeep on your Android device.
   b. If restoring from backup, tap 'Import Encrypted Backup' (.vkeep).
   c. Enter the master password written above to unlock and decrypt.
   d. Never send your master password or backup files over unencrypted email.

4. EMERGENCY CONTACT DESIGNATION:
   Trusted Contact: _________________________________________________
   Relationship:    _________________________________________________
   Date Stored:     _________________________________________________
======================================================================
""".trimIndent()
    }

    private fun escapeCsv(value: String): String {
        if (value.isEmpty()) return ""
        val containsSpecial = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (containsSpecial) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}

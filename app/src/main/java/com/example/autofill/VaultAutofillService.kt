package com.example.autofill

import android.app.Activity
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.R
import com.example.data.VaultEntry
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import com.example.data.VaultState

/**
 * Android Autofill Framework Service for VaultKeep.
 * Strictly respects vault encryption: NEVER leaks credentials without unlock authentication.
 */
class VaultAutofillService : AutofillService() {

    private lateinit var repository: VaultRepository
    private lateinit var preferences: VaultPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = VaultPreferences(applicationContext)
        repository = VaultRepository(applicationContext, preferences)
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        val parsed = parseStructure(structure)
        if (parsed.usernameId == null && parsed.passwordId == null) {
            callback.onSuccess(null)
            return
        }

        val currentVaultState = repository.vaultState.value
        val queryTarget = parsed.webDomain.ifBlank { parsed.packageName }

        if (currentVaultState !is VaultState.Unlocked) {
            // Vault is locked: build an authentication intent requiring unlock
            val authIntent = Intent(this, AutofillAuthActivity::class.java).apply {
                putExtra(AutofillAuthActivity.EXTRA_QUERY_DOMAIN, queryTarget)
                putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, parsed.usernameId)
                putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, parsed.passwordId)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                1001,
                authIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val authPresentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "🔒 Unlock VaultKeep to Autofill")
            }

            val idsToAutofill = listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray()
            val fillResponse = FillResponse.Builder()
                .setAuthentication(idsToAutofill, pendingIntent.intentSender, authPresentation)
                .build()

            callback.onSuccess(fillResponse)
            return
        }

        // Vault is unlocked: look for matching credentials
        val matches = repository.findAutofillMatches(queryTarget)
        val responseBuilder = FillResponse.Builder()

        if (matches.isNotEmpty()) {
            for (entry in matches.take(5)) {
                val datasetBuilder = Dataset.Builder()
                val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                    setTextViewText(android.R.id.text1, entry.title)
                    setTextViewText(android.R.id.text2, entry.username)
                }

                if (parsed.usernameId != null && entry.username.isNotBlank()) {
                    datasetBuilder.setValue(parsed.usernameId, AutofillValue.forText(entry.username), presentation)
                }
                if (parsed.passwordId != null && entry.password.isNotBlank()) {
                    datasetBuilder.setValue(parsed.passwordId, AutofillValue.forText(entry.password), presentation)
                }

                try {
                    responseBuilder.addDataset(datasetBuilder.build())
                } catch (_: Exception) {}
            }
        }

        // Add SaveInfo affordance if user types new credentials
        val saveIds = listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray()
        if (saveIds.isNotEmpty()) {
            val saveInfo = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME,
                saveIds
            ).build()
            responseBuilder.setSaveInfo(saveInfo)
        }

        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure != null) {
            val parsed = parseStructure(structure)
            // If user saved new credentials, open quick save activity
            val saveIntent = Intent(this, AutofillAuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(AutofillAuthActivity.EXTRA_IS_SAVE_MODE, true)
                putExtra(AutofillAuthActivity.EXTRA_QUERY_DOMAIN, parsed.webDomain.ifBlank { parsed.packageName })
                putExtra(AutofillAuthActivity.EXTRA_CAPTURED_USER, parsed.capturedUsername)
                putExtra(AutofillAuthActivity.EXTRA_CAPTURED_PASS, parsed.capturedPassword)
            }
            startActivity(saveIntent)
        }
        callback.onSuccess()
    }

    private data class ParsedStructure(
        val packageName: String,
        val webDomain: String,
        val usernameId: AutofillId?,
        val passwordId: AutofillId?,
        val capturedUsername: String = "",
        val capturedPassword: String = ""
    )

    private fun parseStructure(structure: AssistStructure): ParsedStructure {
        var domain = ""
        val packageName = structure.activityComponent?.packageName ?: ""
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var capturedUser = ""
        var capturedPass = ""

        val nodeCount = structure.windowNodeCount
        for (i in 0 until nodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode
            val result = traverseNode(rootNode)

            if (result.domain.isNotBlank()) domain = result.domain
            if (result.usernameId != null) {
                usernameId = result.usernameId
                capturedUser = result.capturedUser
            }
            if (result.passwordId != null) {
                passwordId = result.passwordId
                capturedPass = result.capturedPass
            }
        }

        return ParsedStructure(
            packageName = packageName,
            webDomain = domain,
            usernameId = usernameId,
            passwordId = passwordId,
            capturedUsername = capturedUser,
            capturedPassword = capturedPass
        )
    }

    private data class NodeResult(
        val domain: String = "",
        val usernameId: AutofillId? = null,
        val passwordId: AutofillId? = null,
        val capturedUser: String = "",
        val capturedPass: String = ""
    )

    private fun traverseNode(node: AssistStructure.ViewNode): NodeResult {
        var domain = node.webDomain ?: ""
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var userText = ""
        var passText = ""

        val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
        val hintText = (node.hint ?: "").toString().lowercase()
        val idEntry = (node.idEntry ?: "").lowercase()

        val isPassword = hints.contains("password") ||
                hintText.contains("password") ||
                idEntry.contains("password") ||
                idEntry.contains("pwd") ||
                (node.inputType and 0x80 != 0) // TYPE_TEXT_VARIATION_PASSWORD

        val isUsername = hints.contains("username") ||
                hints.contains("emailaddress") ||
                hints.contains("email") ||
                hintText.contains("username") ||
                hintText.contains("email") ||
                idEntry.contains("username") ||
                idEntry.contains("email") ||
                idEntry.contains("login")

        if (isPassword && node.autofillId != null) {
            passwordId = node.autofillId
            passText = node.text?.toString() ?: ""
        } else if (isUsername && node.autofillId != null) {
            usernameId = node.autofillId
            userText = node.text?.toString() ?: ""
        }

        for (i in 0 until node.childCount) {
            val childResult = traverseNode(node.getChildAt(i))
            if (domain.isBlank() && childResult.domain.isNotBlank()) domain = childResult.domain
            if (usernameId == null && childResult.usernameId != null) {
                usernameId = childResult.usernameId
                userText = childResult.capturedUser
            }
            if (passwordId == null && childResult.passwordId != null) {
                passwordId = childResult.passwordId
                passText = childResult.capturedPass
            }
        }

        return NodeResult(
            domain = domain,
            usernameId = usernameId,
            passwordId = passwordId,
            capturedUser = userText,
            capturedPass = passText
        )
    }
}

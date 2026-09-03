package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmergencyRecoveryKitDialog(
    saltHex: String,
    passwordHint: String,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentDate = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    val kitJsonPayload = remember(saltHex, passwordHint, currentDate) {
        """
        {
          "app": "VaultKeep",
          "doc": "Emergency Recovery Kit",
          "created_at": "$currentDate",
          "kdf": "PBKDF2WithHmacSHA256",
          "iterations": 310000,
          "cipher": "AES-256-GCM",
          "salt_hex": "$saltHex",
          "hint": "${passwordHint.replace("\"", "\\\"")}"
        }
        """.trimIndent()
    }

    val qrBitmap = remember(kitJsonPayload) {
        generateQrBitmap(kitJsonPayload, 480, 480)
    }

    var copiedToClipboard by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .testTag("emergency_recovery_kit_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Emergency Recovery Kit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Physical cold-storage record",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Scrollable Document Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Safety advice notice
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Print or store this offline in a physical safe. Never upload this document to cloud services.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // QR Code Card
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Recovery Kit QR Code",
                                    modifier = Modifier.size(180.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Vault Cryptographic Parameters (Scannable)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    // Metadata Parameters
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SECURITY PARAMETERS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        RecoveryParamRow(label = "Application", value = "VaultKeep v1.0 (Zero-Knowledge)")
                        RecoveryParamRow(label = "Date Created", value = currentDate)
                        RecoveryParamRow(label = "Key Derivation", value = "PBKDF2WithHmacSHA256 (310,000 rounds)")
                        RecoveryParamRow(label = "Cipher Suite", value = "AES-256-GCM Authenticated")
                        RecoveryParamRow(
                            label = "Vault Salt",
                            value = if (saltHex.isNotBlank()) saltHex.take(32) + "..." else "Derived on first entry",
                            isMonospace = true
                        )
                        RecoveryParamRow(
                            label = "Password Hint",
                            value = passwordHint.ifBlank { "(No hint configured)" }
                        )
                    }

                    // Cold-Storage Instructions
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "HOW TO RECOVER YOUR VAULT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "1. To restore your vault on a new phone, reinstall VaultKeep.\n" +
                                   "2. If you exported an encrypted backup (.vkeep), tap 'Restore Encrypted Backup' in Settings or setup.\n" +
                                   "3. Enter the backup password you selected when exporting.\n" +
                                   "4. If you only remember parts of your password, refer to the hint stored on this sheet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val fullText = """
                            =================================================
                            VAULTKEEP EMERGENCY RECOVERY KIT
                            Zero-Knowledge Vault Parameters
                            =================================================
                            Date: $currentDate
                            App: VaultKeep 1.0 (Fully Local)
                            Key Derivation: PBKDF2WithHmacSHA256 (310,000 rounds)
                            Cipher: AES-256-GCM
                            Vault Salt (Hex): $saltHex
                            Password Hint: $passwordHint
                            
                            INSTRUCTIONS:
                            Store this physical document in a secure offline safe.
                            VaultKeep has no backdoors or cloud resets.
                            Restore using your .vkeep encrypted backup files.
                            =================================================
                            """.trimIndent()
                            clipboardManager.setText(AnnotatedString(fullText))
                            copiedToClipboard = true
                        },
                        modifier = Modifier.weight(1f).testTag("copy_recovery_kit_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedToClipboard) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copiedToClipboard) "Copied" else "Copy Text")
                    }

                    Button(
                        onClick = {
                            printRecoveryKit(context, currentDate, saltHex, passwordHint)
                        },
                        modifier = Modifier.weight(1f).testTag("print_recovery_kit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print / PDF")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryParamRow(label: String, value: String, isMonospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isMonospace) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun generateQrBitmap(content: String, width: Int, height: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

private fun printRecoveryKit(context: Context, date: String, saltHex: String, hint: String) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val webView = WebView(context)
        val htmlDocument = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; padding: 24px; color: #111; max-width: 650px; margin: auto; }
                    h1 { font-size: 22px; border-bottom: 2px solid #000; padding-bottom: 8px; margin-bottom: 4px; }
                    h2 { font-size: 14px; text-transform: uppercase; color: #555; margin-top: 20px; }
                    .box { border: 1px solid #ccc; padding: 14px; border-radius: 8px; background: #fdfdfd; margin: 12px 0; }
                    .row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid #eee; font-size: 13px; }
                    .label { color: #666; }
                    .val { font-weight: bold; font-family: monospace; }
                    .warning { background: #fff3cd; border: 1px solid #ffeeba; padding: 10px; border-radius: 6px; font-size: 12px; }
                </style>
            </head>
            <body>
                <h1>VAULTKEEP EMERGENCY RECOVERY KIT</h1>
                <p style="color: #666; font-size: 12px; margin-top: 2px;">Zero-Knowledge Cryptographic Parameters • Store in Physical Cold Storage</p>
                
                <div class="warning">
                    <strong>CRITICAL:</strong> Keep this physical page in a secure safe or fireproof box. Never scan or upload to cloud storage.
                </div>

                <h2>Vault Parameters</h2>
                <div class="box">
                    <div class="row"><span class="label">Date Created:</span><span class="val">$date</span></div>
                    <div class="row"><span class="label">Application:</span><span class="val">VaultKeep 1.0</span></div>
                    <div class="row"><span class="label">Key Derivation:</span><span class="val">PBKDF2 (310,000 rounds)</span></div>
                    <div class="row"><span class="label">Cipher Suite:</span><span class="val">AES-256-GCM</span></div>
                    <div class="row"><span class="label">Vault Salt:</span><span class="val">${saltHex.take(40)}</span></div>
                    <div class="row"><span class="label">Password Hint:</span><span class="val">$hint</span></div>
                </div>

                <h2>Restoration Instructions</h2>
                <ol style="font-size: 12px; line-height: 1.6; color: #333;">
                    <li>Install VaultKeep on your device.</li>
                    <li>Use your exported <code>.vkeep</code> encrypted backup files.</li>
                    <li>Unlock using the master password you assigned.</li>
                    <li>If you cannot remember your password, refer to the hint recorded above.</li>
                </ol>
            </body>
            </html>
        """.trimIndent()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printAdapter = webView.createPrintDocumentAdapter("VaultKeep-Emergency-Kit")
                val jobName = "VaultKeep Emergency Recovery Kit"
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null)
    } catch (_: Exception) {
        // Fallback share intent
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "VaultKeep Emergency Recovery Kit")
            putExtra(Intent.EXTRA_TEXT, "VaultKeep Emergency Kit\nSalt: $saltHex\nHint: $hint\nDate: $date")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Recovery Kit"))
    }
}

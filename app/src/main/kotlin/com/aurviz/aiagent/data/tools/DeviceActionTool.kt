package com.aurviz.aiagent.data.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.aurviz.aiagent.data.api.FunctionDeclaration
import com.aurviz.aiagent.data.api.SchemaObject
import com.aurviz.aiagent.data.api.SchemaProperty
import com.aurviz.aiagent.data.local.security.SecurePreferences
import com.aurviz.aiagent.domain.models.SensitiveAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class DeviceActionTool(
    private val context: Context,
    private val securePreferences: SecurePreferences
) : AgentTool {

    override val name: String = "device_actions"
    override val description: String =
        "Performs real-world device actions: place phone calls, open camera, or send WhatsApp messages."

    override fun toFunctionDeclaration(): FunctionDeclaration {
        return FunctionDeclaration(
            name = name,
            description = description,
            parameters = SchemaObject(
                type = "OBJECT",
                properties = mapOf(
                    "action" to SchemaProperty(
                        type = "STRING",
                        description = "Action: 'call_phone', 'send_whatsapp', 'open_camera', or 'open_app'",
                        enum = listOf("call_phone", "send_whatsapp", "open_camera", "open_app")
                    ),
                    "phone_number" to SchemaProperty(
                        type = "STRING",
                        description = "Target phone number with optional country code, e.g. '+919876543210' or '9876543210'"
                    ),
                    "message" to SchemaProperty(
                        type = "STRING",
                        description = "Text message body content (for WhatsApp)"
                    ),
                    "contact_name" to SchemaProperty(
                        type = "STRING",
                        description = "Name of the contact if known"
                    ),
                    "app_name" to SchemaProperty(
                        type = "STRING",
                        description = "Name of application to launch: 'whatsapp', 'instagram', 'youtube', 'facebook', 'camera', 'chrome', 'settings'"
                    )
                ),
                required = listOf("action")
            )
        )
    }

    override fun requiresConfirmation(args: Map<String, Any?>): Boolean {
        val action = args["action"]?.toString() ?: ""
        // Phone calls can be sensitive, respect user confirmation setting
        if (action == "call_phone") {
            return securePreferences.requireEmailConfirmation
        }
        return false
    }

    override fun getConfirmationDetails(args: Map<String, Any?>): SensitiveAction? {
        val action = args["action"]?.toString() ?: ""
        if (action == "call_phone") {
            val phone = args["phone_number"]?.toString() ?: ""
            val name = args["contact_name"]?.toString() ?: ""
            return SensitiveAction.MakePhoneCall(
                phoneNumber = phone,
                contactName = name,
                callbackArgs = args
            )
        }
        return null
    }

    override suspend fun execute(args: Map<String, Any?>): ToolExecutionResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.toString() ?: ""

        when (action) {
            "call_phone" -> executeCallPhone(args)
            "send_whatsapp" -> executeWhatsApp(args)
            "open_camera" -> executeOpenCamera()
            "open_app" -> executeOpenApp(args)
            else -> ToolExecutionResult.Failure("Unknown device action: '$action'")
        }
    }

    private fun executeCallPhone(args: Map<String, Any?>): ToolExecutionResult {
        var rawNumber = args["phone_number"]?.toString()?.trim() ?: ""
        val contactName = args["contact_name"]?.toString()?.trim() ?: ""

        if (rawNumber.isBlank() && contactName.isNotBlank()) {
            val resolved = resolveContactPhoneNumber(contactName)
            if (!resolved.isNullOrBlank()) {
                rawNumber = resolved
            }
        }

        if (rawNumber.isBlank()) {
            return ToolExecutionResult.Failure("Phone number cannot be empty for call action${if (contactName.isNotBlank()) " (could not resolve contact '$contactName')" else ""}.")
        }

        // Clean number keeping only digits and leading '+'
        val cleanNumber = rawNumber.replace(Regex("[^0-9+]"), "")

        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)

            val summary = if (contactName.isNotBlank()) {
                "Initiating phone call to $contactName ($cleanNumber).\nDialer dispatched on device."
            } else {
                "Initiating phone call to $cleanNumber.\nDialer dispatched on device."
            }

            ToolExecutionResult.Success(
                outputSummary = summary,
                structuredData = mapOf(
                    "action" to "call_phone",
                    "phoneNumber" to cleanNumber,
                    "contactName" to contactName,
                    "status" to "DIALER_DISPATCHED"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch phone dialer: ${e.message}", e)
            ToolExecutionResult.Failure("Could not launch phone dialer: ${e.message}")
        }
    }

    private fun executeWhatsApp(args: Map<String, Any?>): ToolExecutionResult {
        val message = args["message"]?.toString()?.trim() ?: "Hello!"
        var rawNumber = args["phone_number"]?.toString()?.trim() ?: ""
        val contactName = args["contact_name"]?.toString()?.trim() ?: ""

        if (rawNumber.isBlank() && contactName.isNotBlank()) {
            val resolved = resolveContactPhoneNumber(contactName)
            if (!resolved.isNullOrBlank()) {
                rawNumber = resolved
            }
        }

        val cleanNumber = rawNumber.replace(Regex("[^0-9]"), "")

        return try {
            val intent = if (cleanNumber.isNotBlank()) {
                // Direct WhatsApp message to specific number (instantly opens their chat window)
                val formattedNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
                val url = "https://api.whatsapp.com/send?phone=$formattedNumber&text=${URLEncoder.encode(message, "UTF-8")}"
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                // Generic WhatsApp share
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // If package com.whatsapp is not found, launch normal text share
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }

            val summary = if (cleanNumber.isNotBlank()) {
                val targetDesc = if (contactName.isNotBlank()) "$contactName ($cleanNumber)" else cleanNumber
                "WhatsApp chat opened directly for $targetDesc with message:\n\"$message\""
            } else {
                "WhatsApp opened with message:\n\"$message\""
            }

            ToolExecutionResult.Success(
                outputSummary = summary,
                structuredData = mapOf(
                    "action" to "send_whatsapp",
                    "phoneNumber" to cleanNumber,
                    "contactName" to contactName,
                    "message" to message,
                    "status" to "WHATSAPP_DISPATCHED"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch WhatsApp intent: ${e.message}", e)
            ToolExecutionResult.Failure("Could not open WhatsApp: ${e.message}")
        }
    }

    private fun resolveContactPhoneNumber(contactName: String): String? {
        if (contactName.isBlank()) return null
        return try {
            val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIdx >= 0) {
                        val raw = cursor.getString(numIdx) ?: ""
                        return raw.replace(Regex("[^0-9]"), "")
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Contacts permission not granted or query failed: ${e.message}")
            null
        }
    }

    private fun executeOpenCamera(): ToolExecutionResult {
        return try {
            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(cameraIntent)
            } catch (e: Exception) {
                val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(captureIntent)
            }

            ToolExecutionResult.Success(
                outputSummary = "Device camera opened successfully.",
                structuredData = mapOf(
                    "action" to "open_camera",
                    "status" to "CAMERA_OPENED"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera: ${e.message}", e)
            ToolExecutionResult.Failure("Could not open camera application: ${e.message}")
        }
    }

    private fun executeOpenApp(args: Map<String, Any?>): ToolExecutionResult {
        val appName = args["app_name"]?.toString()?.lowercase()?.trim() ?: "whatsapp"
        val packageMap = mapOf(
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "youtube" to "com.google.android.youtube",
            "facebook" to "com.facebook.katana",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "telegram" to "org.telegram.messenger",
            "camera" to "camera",
            "settings" to "settings"
        )

        if (appName == "camera") {
            return executeOpenCamera()
        }

        if (appName == "settings") {
            return try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult.Success(
                    outputSummary = "System Settings opened successfully.",
                    structuredData = mapOf("action" to "open_app", "app" to "settings")
                )
            } catch (e: Exception) {
                ToolExecutionResult.Failure("Could not open Settings: ${e.message}")
            }
        }

        val targetPkg = packageMap[appName] ?: appName

        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ToolExecutionResult.Success(
                    outputSummary = "Opened ${appName.replaceFirstChar { it.uppercase() }} application successfully.",
                    structuredData = mapOf("action" to "open_app", "packageName" to targetPkg)
                )
            } else {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$targetPkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(marketIntent)
                    ToolExecutionResult.Success(
                        outputSummary = "Application '$appName' not found locally; opened Play Store.",
                        structuredData = mapOf("action" to "open_app", "packageName" to targetPkg)
                    )
                } catch (e2: Exception) {
                    ToolExecutionResult.Failure("Application '$appName' ($targetPkg) is not installed on this device.")
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Could not launch $appName: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DeviceActionTool"
    }
}

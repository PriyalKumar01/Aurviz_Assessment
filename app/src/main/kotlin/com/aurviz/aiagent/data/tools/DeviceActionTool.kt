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
            // If cleanNumber has 10 to 12 digits, attempt direct chat window
            var directChatLaunched = false
            if (cleanNumber.length in 10..12) {
                val formattedNumber = when {
                    cleanNumber.length == 10 -> "91$cleanNumber"
                    cleanNumber.length == 11 && cleanNumber.startsWith("0") -> "91${cleanNumber.substring(1)}"
                    cleanNumber.length == 12 && cleanNumber.startsWith("91") -> cleanNumber
                    else -> cleanNumber
                }
                try {
                    val url = "https://api.whatsapp.com/send?phone=$formattedNumber&text=${URLEncoder.encode(message, "UTF-8")}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    directChatLaunched = true
                } catch (e: Exception) {
                    Log.w(TAG, "Direct WhatsApp URL failed, falling back to share intent: ${e.message}")
                }
            }

            // Fallback: If contact not directly resolved or direct link failed, open WhatsApp with text pre-filled
            // The user can tap ANY contact (e.g. Papa Ji Jio) and the message is ALREADY typed in the input box!
            if (!directChatLaunched) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(shareIntent)
                } catch (e2: Exception) {
                    val genericIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(genericIntent)
                }
            }

            val summary = if (cleanNumber.isNotBlank() && contactName.isNotBlank()) {
                "WhatsApp opened for $contactName with message:\n\"$message\""
            } else if (contactName.isNotBlank()) {
                "WhatsApp opened with message ready for $contactName:\n\"$message\""
            } else {
                "WhatsApp opened with message pre-filled:\n\"$message\""
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
        val lowerTarget = contactName.lowercase().trim()

        // Handle self messaging ("khud", "myself", "me", "apne aap")
        if (lowerTarget in listOf("khud", "myself", "self", "me", "apne aap", "apne", "my own", "mera number")) {
            val userPhone = securePreferences.userPhoneNumber.trim()
            if (userPhone.isNotBlank()) {
                return userPhone.replace(Regex("[^0-9+]"), "")
            }
        }

        return try {
            val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val numIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                var bestNumber: String? = null
                var bestScore = 0

                // Split tokens e.g. "papa ji jio" -> ["papa", "ji", "jio"]
                val targetTokens = lowerTarget.split(" ", "_", "-").filter { it.length > 1 }

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)?.lowercase() ?: continue
                    val num = cursor.getString(numIdx) ?: continue

                    // Exact match
                    if (name == lowerTarget) {
                        return num.replace(Regex("[^0-9+]"), "")
                    }

                    // Count token overlap
                    var score = 0
                    for (token in targetTokens) {
                        if (name.contains(token)) score += 2
                    }
                    if (name.contains(lowerTarget)) score += 5

                    if (score > bestScore) {
                        bestScore = score
                        bestNumber = num.replace(Regex("[^0-9+]"), "")
                    }
                }

                if (bestScore > 0) {
                    return bestNumber
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

        if (appName in listOf("camera", "cam")) {
            return executeOpenCamera()
        }

        if (appName in listOf("settings", "setting", "system settings")) {
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

        val packageMap = mapOf(
            "zomato" to "com.application.zomato",
            "swiggy" to "in.swiggy.android",
            "instagram" to "com.instagram.android",
            "insta" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "yt" to "com.google.android.youtube",
            "facebook" to "com.facebook.katana",
            "fb" to "com.facebook.katana",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "telegram" to "org.telegram.messenger",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "uber" to "com.ubercab",
            "ola" to "com.olacabs.customer"
        )

        val targetPkg = packageMap[appName]

        // 1. Try launching through known package map
        if (targetPkg != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ToolExecutionResult.Success(
                    outputSummary = "Opened ${appName.replaceFirstChar { it.uppercase() }} application successfully.",
                    structuredData = mapOf("action" to "open_app", "packageName" to targetPkg)
                )
            }
        }

        // 2. Dynamic discovery across all installed launcher applications
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = context.packageManager.queryIntentActivities(mainIntent, 0)
            val matchedApp = apps.firstOrNull { resolveInfo ->
                val label = resolveInfo.loadLabel(context.packageManager).toString().lowercase()
                val pkg = resolveInfo.activityInfo.packageName.lowercase()
                label.contains(appName) || pkg.contains(appName)
            }

            if (matchedApp != null) {
                val matchedPkg = matchedApp.activityInfo.packageName
                val launchIntent = context.packageManager.getLaunchIntentForPackage(matchedPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val label = matchedApp.loadLabel(context.packageManager).toString()
                    return ToolExecutionResult.Success(
                        outputSummary = "Opened $label application successfully.",
                        structuredData = mapOf("action" to "open_app", "packageName" to matchedPkg)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dynamic app search failed: ${e.message}")
        }

        // 3. Fallback to Google Play Store
        val fallbackPkg = targetPkg ?: appName
        return try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$fallbackPkg")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            ToolExecutionResult.Success(
                outputSummary = "Application '$appName' not found locally; opened Play Store.",
                structuredData = mapOf("action" to "open_app", "packageName" to fallbackPkg)
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Could not launch $appName: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DeviceActionTool"
    }
}

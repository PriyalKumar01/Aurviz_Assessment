package com.aurviz.aiagent.domain.models

sealed class SensitiveAction {
    abstract val title: String
    abstract val description: String
    abstract val callbackArgs: Map<String, Any?>

    data class SendEmail(
        val recipient: String,
        val subject: String,
        val body: String,
        val account: String = "",
        override val callbackArgs: Map<String, Any?> = emptyMap()
    ) : SensitiveAction() {
        override val title: String = "Confirm Email Dispatch"
        override val description: String = "Sending email to $recipient via connected account."
    }

    data class DeleteMemory(
        val memoryId: String,
        val memoryKey: String,
        override val callbackArgs: Map<String, Any?> = emptyMap()
    ) : SensitiveAction() {
        override val title: String = "Confirm Memory Deletion"
        override val description: String = "Permanently deleting saved memory '$memoryKey'."
    }

    data class MakePhoneCall(
        val phoneNumber: String,
        val contactName: String = "",
        override val callbackArgs: Map<String, Any?> = emptyMap()
    ) : SensitiveAction() {
        override val title: String = "Confirm Phone Call"
        override val description: String = "Placing a phone call to ${contactName.ifBlank { phoneNumber }}."
    }
}

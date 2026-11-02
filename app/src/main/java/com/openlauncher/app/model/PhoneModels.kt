package com.openlauncher.app.model

data class PhoneContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)

data class IncomingMessage(
    val key: String,
    val sender: String,
    val content: String,
    val packageName: String
)

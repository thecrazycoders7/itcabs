package com.itcabs.domain.model

/** One chat message on a leg. [createdAt] is an ISO-8601 instant string from the server. */
data class ChatMessage(
    val id: Long,
    val legId: Long,
    val senderId: Long,
    val body: String,
    val createdAt: String,
)

package com.itcabs.data

import com.itcabs.core.network.ChatApi
import com.itcabs.core.network.MessageDto
import com.itcabs.core.network.SendMessageDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.ChatMessage
import com.itcabs.domain.repository.ChatRepository

class ChatRepositoryImpl(private val api: ChatApi) : ChatRepository {
    override suspend fun messages(legId: Long): AppResult<List<ChatMessage>> =
        api.messages(legId).asResult { it.map(MessageDto::toDomain) }

    override suspend fun send(legId: Long, body: String): AppResult<ChatMessage> =
        api.send(legId, SendMessageDto(body)).asResult { it.toDomain() }
}

private fun MessageDto.toDomain() = ChatMessage(id, legId, senderId, body, createdAt)

package com.itcabs.data

import com.itcabs.core.network.ChatApi
import com.itcabs.core.network.MessageDto
import com.itcabs.core.network.SendMessageDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.ChatMessage
import com.itcabs.domain.repository.ChatRepository

class ChatRepositoryImpl(private val api: ChatApi) : ChatRepository {
    override suspend fun messages(id: Long, companyJob: Boolean): AppResult<List<ChatMessage>> =
        (if (companyJob) api.companyMessages(id) else api.messages(id)).asResult { it.map(MessageDto::toDomain) }

    override suspend fun send(id: Long, body: String, companyJob: Boolean): AppResult<ChatMessage> =
        (if (companyJob) api.companySend(id, SendMessageDto(body)) else api.send(id, SendMessageDto(body))).asResult { it.toDomain() }
}

private fun MessageDto.toDomain() = ChatMessage(id, legId, senderId, body, createdAt)

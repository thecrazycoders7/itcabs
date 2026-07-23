package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.ChatMessage

/** Per-leg chat between the coordinator and the claiming driver (M7). Backend enforces membership. */
interface ChatRepository {
    suspend fun messages(legId: Long): AppResult<List<ChatMessage>>
    suspend fun send(legId: Long, body: String): AppResult<ChatMessage>
}

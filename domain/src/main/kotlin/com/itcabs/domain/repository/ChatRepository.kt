package com.itcabs.domain.repository

import com.itcabs.domain.AppResult
import com.itcabs.domain.model.ChatMessage

/** Per-leg chat between the coordinator and the claiming driver (M7). Backend enforces membership. */
interface ChatRepository {
    /** [companyJob]=true routes to the multi-stop company-job thread instead of a leg. */
    suspend fun messages(id: Long, companyJob: Boolean = false): AppResult<List<ChatMessage>>
    suspend fun send(id: Long, body: String, companyJob: Boolean = false): AppResult<ChatMessage>
}

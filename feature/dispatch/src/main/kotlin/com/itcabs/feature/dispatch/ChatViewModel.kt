package com.itcabs.feature.dispatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.ChatMessage
import com.itcabs.domain.repository.AuthRepository
import com.itcabs.domain.repository.ChatRepository
import com.itcabs.domain.repository.DispatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val myUserId: Long? = null,
    val input: String = "",
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val dispatch: DispatchRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var legId: Long = -1
    private var started = false

    /** Bind to a leg's thread: load who-am-I + messages, and refresh live on WS events. */
    fun open(legId: Long) {
        if (started) return
        started = true
        this.legId = legId
        viewModelScope.launch {
            (auth.currentUser() as? AppResult.Ok)?.let { u -> _state.update { it.copy(myUserId = u.value.id) } }
        }
        refresh()
        viewModelScope.launch { dispatch.legEvents().collect { refresh() } }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun refresh() {
        viewModelScope.launch {
            when (val result = chat.messages(legId)) {
                is AppResult.Ok -> _state.update { it.copy(messages = result.value) }
                is AppResult.Err -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun sendMessage() {
        val body = _state.value.input.trim()
        if (body.isEmpty()) return
        _state.update { it.copy(input = "") }
        viewModelScope.launch {
            when (val result = chat.send(legId, body)) {
                is AppResult.Ok -> refresh()
                is AppResult.Err -> _state.update { it.copy(error = result.message) }
            }
        }
    }
}

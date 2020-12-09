package com.example.kotlin.chat.service

import com.example.kotlin.chat.asDomainObject
import com.example.kotlin.chat.asRendered
import com.example.kotlin.chat.mapToViewModel
import com.example.kotlin.chat.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collect
import org.springframework.stereotype.Service

@Service
class PersistentMessageService(val messageRepository: MessageRepository) : MessageService {

    val sender: MutableSharedFlow<MessageVM> = MutableSharedFlow()

    override fun latest(): Flow<MessageVM> =
        messageRepository.findLatest()
            .mapToViewModel()

    override fun after(messageId: String): Flow<MessageVM> =
        messageRepository.findLatest(messageId)
            .mapToViewModel()

    override fun stream(): Flow<MessageVM> = sender

    override suspend fun post(messages: Flow<MessageVM>) =
        messages
            .onEach { sender.emit(it.asRendered()) }
            .map { it.asDomainObject() }
            .let { messageRepository.saveAll(it) }
            .collect()
}
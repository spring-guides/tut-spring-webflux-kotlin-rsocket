package com.example.kotlin.chat.service

import com.example.kotlin.chat.asDomainObject
import com.example.kotlin.chat.asRendered
import com.example.kotlin.chat.mapToViewModel
import com.example.kotlin.chat.repository.MessageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collect
import org.springframework.stereotype.Service

@Service
@ExperimentalCoroutinesApi
class PersistentMessageService(val messageRepository: MessageRepository) : MessageService {

    val sender: BroadcastChannel<MessageVM> = BroadcastChannel(Channel.BUFFERED)

    override fun latest(): Flow<MessageVM> =
        messageRepository.findLatest()
            .mapToViewModel()

    override fun after(messageId: String): Flow<MessageVM> =
        messageRepository.findLatest(messageId)
            .mapToViewModel()

    override fun stream(): Flow<MessageVM> = sender.openSubscription().receiveAsFlow()

    override suspend fun post(messages: Flow<MessageVM>) =
        messages
            .onEach { sender.send(it.asRendered()) }
            .map {  it.asDomainObject() }
            .let { messageRepository.saveAll(it) }
            .collect()
}
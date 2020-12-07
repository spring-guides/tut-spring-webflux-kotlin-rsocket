package com.example.kotlin.chat.service

import com.example.kotlin.chat.repository.ContentType
import com.example.kotlin.chat.repository.Message
import com.example.kotlin.chat.repository.MessageRepository
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.net.URL

@Service
@Primary
class PersistentMessageService(val messageRepository: MessageRepository) : MessageService {

    override fun latest(): List<MessageVM> =
            messageRepository.findLatest()
                    .map { with(it) { MessageVM(content, UserVM(username, URL(userAvatarImageLink)), sent, id) } }

    override fun after(messageId: String): List<MessageVM> =
            messageRepository.findLatest(messageId)
                    .map { with(it) { MessageVM(content, UserVM(username, URL(userAvatarImageLink)), sent, id) } }

    override fun post(message: MessageVM) {
        messageRepository.save(
                with(message) { Message(content, ContentType.PLAIN, sent, user.name, user.avatarImageLink.toString()) }
        )
    }
}
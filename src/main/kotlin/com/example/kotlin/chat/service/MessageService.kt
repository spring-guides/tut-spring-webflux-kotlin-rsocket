package com.example.kotlin.chat.service

interface MessageService {

    fun latest(): List<MessageVM>

    fun after(messageId: String): List<MessageVM>

    fun post(message: MessageVM)
}
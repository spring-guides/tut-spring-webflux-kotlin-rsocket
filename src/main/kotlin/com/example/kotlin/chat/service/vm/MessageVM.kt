package com.example.kotlin.chat.service.vm

import java.time.Instant

data class MessageVM(val content: String, val user: UserVM, val sent: Instant, val id: String? = null)
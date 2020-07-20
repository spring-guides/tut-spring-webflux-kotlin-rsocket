package com.example.kotlin.chat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatKotlinApplication

fun main(args: Array<String>) {
	runApplication<ChatKotlinApplication>(*args)
}
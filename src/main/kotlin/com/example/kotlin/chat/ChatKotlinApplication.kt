package com.example.kotlin.chat

import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ClassPathResource
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator

@SpringBootApplication
class ChatKotlinApplication

fun main(args: Array<String>) {
	runApplication<ChatKotlinApplication>(*args)
}

@Bean
fun initializer(connectionFactory: ConnectionFactory): ConnectionFactoryInitializer {
	val initializer = ConnectionFactoryInitializer()
	initializer.setConnectionFactory(connectionFactory)
	val populator = CompositeDatabasePopulator()
	populator.addPopulators(ResourceDatabasePopulator(ClassPathResource("./sql/schema.sql")))
	initializer.setDatabasePopulator(populator)
	return initializer
}

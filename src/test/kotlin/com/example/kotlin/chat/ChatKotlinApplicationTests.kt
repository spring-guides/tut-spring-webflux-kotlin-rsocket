package com.example.kotlin.chat

import com.example.kotlin.chat.repository.ContentType
import com.example.kotlin.chat.repository.Message
import com.example.kotlin.chat.repository.MessageRepository
import com.example.kotlin.chat.service.MessageVM
import com.example.kotlin.chat.service.UserVM
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.postForEntity
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.RequestEntity
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb"
    ]
)
class ChatKotlinApplicationTests {

    @Autowired
    lateinit var client: TestRestTemplate

    @Autowired
    lateinit var messageRepository: MessageRepository

    lateinit var lastMessageId: String

    val now: Instant = Instant.now()

    @BeforeEach
    fun setUp() {
        val secondBeforeNow = now.minusSeconds(1)
        val twoSecondBeforeNow = now.minusSeconds(2)
        val savedMessages = messageRepository.saveAll(
            listOf(
                Message(
                    "*testMessage*",
                    ContentType.PLAIN,
                    twoSecondBeforeNow,
                    "test",
                    "http://test.com"
                ),
                Message(
                    "**testMessage2**",
                    ContentType.PLAIN,
                    secondBeforeNow,
                    "test1",
                    "http://test.com"
                ),
                Message(
                    "`testMessage3`",
                    ContentType.PLAIN,
                    now,
                    "test2",
                    "http://test.com"
                )
            )
        )
        lastMessageId = savedMessages.first().id ?: ""
    }

    @AfterEach
    fun tearDown() {
        messageRepository.deleteAll()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `test that messages API returns latest messages`(withLastMessageId: Boolean) {
        val messages: List<MessageVM>? = client.exchange(
            RequestEntity<Any>(
                HttpMethod.GET,
                URI("/api/v1/messages?lastMessageId=${if (withLastMessageId) lastMessageId else ""}")
            ),
            object : ParameterizedTypeReference<List<MessageVM>>() {}).body

        if (!withLastMessageId) {
            assertThat(messages?.map { with(it) { copy(id = null, sent = sent.truncatedTo(MILLIS)) } })
                .first()
                .isEqualTo(
                    MessageVM(
                        "*testMessage*",
                        UserVM("test", URL("http://test.com")),
                        now.minusSeconds(2).truncatedTo(MILLIS)
                    )
                )
        }

        assertThat(messages?.map { with(it) { copy(id = null, sent = sent.truncatedTo(MILLIS)) } })
            .containsSubsequence(
                MessageVM(
                    "**testMessage2**",
                    UserVM("test1", URL("http://test.com")),
                    now.minusSeconds(1).truncatedTo(MILLIS)
                ),
                MessageVM(
                    "`testMessage3`",
                    UserVM("test2", URL("http://test.com")),
                    now.truncatedTo(MILLIS)
                )
            )
    }


	@Test
	fun `test that messages posted to the API is stored`() {
		client.postForEntity<Any>(
			URI("/api/v1/messages"),
			MessageVM(
				"`HelloWorld`",
				UserVM("test", URL("http://test.com")),
				now.plusSeconds(1)
			)
		)

		messageRepository.findAll()
			.first { it.content.contains("HelloWorld") }
			.run {
				assertThat(this.copy(id = null, sent = sent.truncatedTo(MILLIS)))
					.isEqualTo(Message(
						"`HelloWorld`",
						ContentType.PLAIN,
						now.plusSeconds(1).truncatedTo(MILLIS),
						"test",
						"http://test.com"
					))
			}
	}
}

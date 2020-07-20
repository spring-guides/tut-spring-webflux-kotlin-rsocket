# Implementing Persistable Messages Service

Welcome to the second step of our tutorial.

At this step of the tutorial, we will be implementing a persisting version of the `MessageService` interface using Spring Data JDBC and H2 as the database. We will introduce the following classes:

 * `PersistentMessageService` - database based implementation of the `MessageService` interface, which will interact with the real data storage via Spring Data `Repository` API.
 * `MessageRepository` - a dedicated `Repository` extension used by `MessageService`  
  
First of all, we have to add required dependencies to the project. For that we need to add into the `build.gradle.kt` file the following `dependencies`:
 
```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
runtimeOnly("com.h2database:h2")
```

> ⚠️ **Note**, in this example, we use `spring-data-jdbc` as a lightweight and straightforward way for JDBC usage in Spring Framework. If you wish to see an example of JPA usage, please see the following [blog-post](https://spring.io/guides/tutorials/spring-boot-kotlin/?#_persistence_with_jpa). 

Once the dependencies are added and resolved, we can start modeling our database schema. Since this is a demo project, we will not be designing something complex and will stick to the following structure:
 
```h2
CREATE TABLE IF NOT EXISTS messages (
  id                     VARCHAR(60)  DEFAULT RANDOM_UUID() PRIMARY KEY,
  content                VARCHAR      NOT NULL,
  content_type           VARCHAR(128) NOT NULL,
  sent                   TIMESTAMP    NOT NULL,
  username               VARCHAR(60)  NOT NULL,
  user_avatar_image_link VARCHAR(256) NOT NULL
);
```

> ⌨️ Put the above SQL code into the `src/main/resources/sql/schema.sql` file. Also, modify `application.properties` so it contains the following configurations:
>```properties
>spring.datasource.schema=classpath:sql/schema.sql
>spring.datasource.url=jdbc:h2:file:./build/data/testdb
>spring.datasource.driverClassName=org.h2.Driver
>spring.datasource.username=sa
>spring.datasource.password=password
>```

Using Spring Data, the mentioned table can be expressed as the following domain object:

```kotlin
@Table("MESSAGES")
data class Message(
    val content: String,
    val contentType: ContentType,
    val sent: Instant,
    val username: String,
    val userAvatarImageLink: String,
    @Id var id: String? = null)

enum class ContentType {
    PLAIN
}
```   

There are a few noticeable things that require explanation. Fields like `content`, `sent`, and `id` mirrors the `MessageVM` class. However, to decrease the number of tables and simplify the final relationship structure, we flatten the `User` object and make its fields a part of the `Message` class. Apart from that, we have a new extra field called `contentType`. This field is created to indicate the content type of the stored message. Since most modern chats support different markup languages, it is common to support different message content encodings. In the beginning, we will support `PLAIN` text, but during step 4, we will extend `ContentType` to support the `MARKDOWN` type.  
      
Once we have the table representation as a class, we may introduce convenient access to the data via `Repository`:
 
```kotlin
interface MessageRepository : CrudRepository<Message, String> {

    // language=SQL
    @Query("""
        SELECT * FROM (
            SELECT * FROM MESSAGES
            ORDER BY "SENT" DESC
            LIMIT 10
        ) ORDER BY "SENT"
    """)
    fun findLatest(): List<Message>

    // language=SQL
    @Query("""
        SELECT * FROM (
            SELECT * FROM MESSAGES
            WHERE SENT > (SELECT SENT FROM MESSAGES WHERE ID = :id)
            ORDER BY "SENT" DESC 
        ) ORDER BY "SENT"
    """)
    fun findLatest(@Param("id") id: String): List<Message>
}
``` 

> ⚠️ Put your `MessageRepository` at the `src/main/kotlin/com/example/kotlin/chat/repository` folder to follow the general project structure. 

Our `MessageRepository` extends an ordinary `CrudRepository` and provides two different methods with the custom queries for retrieving the latest messages and messages after the specific message ID.
 
> 💡 Did you notice [String literals](https://kotlinlang.org/docs/reference/basic-types.html#string-literals) used to express the SQL query in the readable format? Kotlin provides a set of useful additions for Strings. You may learn more about it in the Kotlin lang [docs](https://kotlinlang.org/docs/reference/basic-types.html#strings)   
 
The next step that we have to do is implement the `MessageService` class that integrates with the `MessageRepository` class.
 
```kotlin
@Service
class PersistentMessageService(val messageRepository: MessageRepository) : MessageService {

    override fun latest(): List<MessageVM> =
            messageRepository.findLatest()
                    .map { with(it) { MessageVM(content, UserVM(username, URL(userAvatarImageLink)), sent, id) } }

    override fun latestAfter(lastMessageId: String): List<MessageVM> =
            messageRepository.findLatest(lastMessageId)
                    .map { with(it) { MessageVM(content, UserVM(username, URL(userAvatarImageLink)), sent, id) } }

    override fun post(message: MessageVM) {
        messageRepository.save(
                with(message) { Message(content, ContentType.PLAIN, sent, user.name, user.avatarImageLink.toString()) }
        )
    }
}
```

> ⚠️ Put your `PersistentMessageService` at the `src/main/kotlin/com/example/kotlin/chat/service/impl` 
> folder replacing the previous `FakeMessageService` implementation. 

As we may see from the example above, the `PersistentMessageService` does not seem complicated. On the one hand, the implementation looks like a thin layer for the `MessageRepository`, since what we do here is just a simple object mapping. All business queries are on the `Repository` level. On the other hand, this implementation's simplicity is the merit of the Kotlin language, which provides extensions like `map` and keywords like `with`. 

> 💡 Note, within the `with` keyword's scope, there is no need to refer the object every time we need to access its fields.
 
So far, if we run this application, we will see empty tests page again. However, if we type-in a message into the text input and send it, we will see it appeared on the screen a few moments later. If we open a new browser page, we will see this message again as a part of historical messages.
 
Finally, once the correctness is confirmed manually, we may write a few integration tests to ensure that our code will work properly over time. 

To begin with, we have to modify `ChatKotlinApplicationTests` file and add fields we would need further to use in tests: 
 
```kotlin
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
}
```

As you may notice, in the example above, we use the [lateinit](https://kotlinlang.org/docs/reference/properties.html#late-initialized-properties-and-variables)  keyword, which perfectly fits for the case where non-null fields initialization has to be deferred. In our case, we use it to `@Autowire` the `MessageRepository` field and resolve `TestRestTemplate`.

For simplicity, we will be testing three general cases - messages resolution where `lastMessageId` is not available and where `lastMessageId` is present, and in the case of message sending.
  
To test messages resolving, we have to prepare some tests messages beforehand as well as cleanup the storage after the completion of each case:

```kotlin
@BeforeEach
fun setUp() {
    val secondBeforeNow = now.minusSeconds(1)
    val twoSecondBeforeNow = now.minusSeconds(2)
    val savedMessages = messageRepository.saveAll(listOf(
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
    ))
    lastMessageId = savedMessages.first().id ?: ""
}

@AfterEach
fun tearDown() {
    messageRepository.deleteAll()
}
```

Once the preparation is done, we can create a first tests case for messages retrieval cases:   

```kotlin
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
                .isEqualTo(MessageVM(
                        "*testMessage*",
                        UserVM("test", URL("http://test.com")),
                        now.minusSeconds(2).truncatedTo(MILLIS)
                ))
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
```

From the above example, we may learn a few exciting things that Kotlin offers to its users. First of all, we can see how much better protection against nullability we have with Kotlin. Since the `.exchange` method returns a nullable collection, we have to explicitly accomplish all the places where the properties/methods on this collection are used with the question mark. On the other hand, we do not have to do anything beyond to make our code look as natural as there are no nullable elements at all. Secondly, all data classes have methods like [`copy`](https://kotlinlang.org/docs/reference/data-classes.html#copying), which let you do a full copy of the instance customizing required fields. It is very useful in our case since we may want to truncate received `sent` time to the same time units for comparison purposes. Thirdly, Kotlin supports [String templates](https://kotlinlang.org/docs/reference/basic-types.html#string-templates) is an excellent addition for testing and not only.
 
Once we have this test implemented, the last piece that we have to implement is message
 posting test:
 
```kotlin
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
            .apply {
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
```   

The above test looks similar to the previous, except we check that the posted messages are stored in the database. In this example, we can see the [`apply`](https://kotlinlang.org/docs/reference/scope-functions.html#apply) function, which allows using the target object within the invocation scope as `this`.

Once we have all these tests implemented, we may run them and see that they are passing.

We have implemented a Chat Application version during this step, which works with the database allowing messages persisting and broadcasting to all active users in the chat. Also, now we can access the historical data, so everyone can read previous messages if needed.

This implementation may look complete, though the code we wrote has a few places for improvements. Therefore, we will see how our code can be improved with Kotlin extensions during the next step.  
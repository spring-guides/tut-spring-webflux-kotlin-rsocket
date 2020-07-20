# Implementing Extensions

Welcome to the third step of our tutorial.

At this step of the tutorial, we will be implementing extension methods to decrease the number of code repetition in a few places. For example, you may notice that at this moment, `Messages` <--> `MessageVM` conversion happens explicitly in the `PersistableMessageService`. Apart, we may want to extend the support for a different `contentType`, so we may add support for types like `MARKDOWN`, etc.
 
At first, we may want to reduce the number of repetition so further we can change objects' conversion only at a single place. For that purpose, lets write an extension methods for `Message` and `MessageVM` which add to their instance methods responsible for conversion:
 
```kotlin
fun MessageVM.asDomainObject(contentType: ContentType = ContentType.PLAIN): Message = Message(
        content,
        contentType,
        sent,
        user.name,
        user.avatarImageLink.toString(),
        id
)

fun Message.asViewModel(): MessageVM = MessageVM(
        content,
        UserVM(username, URL(userAvatarImageLink)),
        sent,
        id
)
```

> ⚠️ Let's store the above functions in the `src/main/kotlin/com/example/kotlin/chat/extensions/MessageExtensions.kt` file.  

Now, having extensions methods for `MessageVM` and `Message` conversion, we may use them in the `PersistentMessageService`:

```kotlin
@Service
class PersistentMessageService(val messageRepository: MessageRepository) : MessageService {

    override fun latest(): List<MessageVM> =
            messageRepository.findLatest()
                    .map { it.asViewModel() }

    override fun latestAfter(lastMessageId: String): List<MessageVM> =
            messageRepository.findLatest(lastMessageId)
                    .map { it.asViewModel() }

    override fun post(message: MessageVM) {
        messageRepository.save(message.asDomainObject())
    }
}
```

The code above is better than before. However, we can improve a few more places. As we can see, we use the same `.map` operators with the same function mapper twice. In fact,  we can improve that byte writing a custom map function for a `List` with a specific generic type:
 
```kotlin
fun List<Message>.mapToViewModel(): List<MessageVM> = map { it.asViewModel() }
```

By doing that, Kotlin will provide the mentioned extension method to `List`s which generic type corresponds to the specified one:

```kotlin
@Service
class PersistentMessageService(val messageRepository: MessageRepository) : MessageService {

    override fun latest(): List<MessageVM> =
            messageRepository.findLatest()
                    .mapToViewModel() // now we can use the mentioned extension on List<Message>

    override fun latestAfter(lastMessageId: String): List<MessageVM> =
            messageRepository.findLatest(lastMessageId)
                    .mapToViewModel()
    ...
}
```
 
> ⚠️ **Note**, you cannot use the same extension name for the same class with different generic types. The reason for that is the [type erasure](https://kotlinlang.org/docs/reference/generics.html#type-erasure), which means that at the runtime, we will have the same method for the same class, so it will not be possible to guess which one should be invoked.  
 
Once all the extensions are applied, we can do similar trick and declare supportive extensions for usage in test classes: 

```kotlin
fun MessageVM.prepareForTesting() = copy(id = null, sent = sent.truncatedTo(MILLIS))

fun Message.prepareForTesting() = copy(id = null, sent = sent.truncatedTo(MILLIS))
```

> ⚠️ This time, the above functions should be stored `src/test/kotlin/com/example/kotlin/chat/extensions/TestMessageExtensions.kt` 
> file.  


Once we have the foundational extensions, we can move forward and implement the support of the `MARKDOWN` content type. First of all, we need to add the tooling for the markdown content rendering. For that purpose, we can use an [official markdown library](https://github.com/valich/intellij-markdown) from JetBrains
 
```kotlins
implementation("org.jetbrains:markdown:0.1.45")
```
 
Since we have already learned how to use extensions, lets create another one for the `ContenType` enum, so each enum value will know how to render a specific content:
 
```kotlin
fun ContentType.render(content: String): String = when (this) {
    ContentType.PLAIN -> content
}
```

In the above example, we use `when` expression, which effectively is a pattern-matching implementation in Kotlin. By default, `when` expression requires to always provide a result of the execution. Thus, when it is being used on the undetermined value (for instance, `when` on a `String` input), we always have to provide `else` to return at least something when none of the defined cases matched. However, when the `when` expression is used with the exhaustive values (e.g. `enum` with a constant number of outcomes or `sealed` classes with the defined number of subclasses) - the definition of the `else` branch is redundant. The example above is exactly the case when we know at compile-time all the possible outcomes (and all of them are handled), so the `else` branch is unneeded.
 
Once, we know how `when` expression works, lets finally add a new value into the `ContentType` enum:
 
```kotlin
enum class ContentType {
    PLAIN, MARKDOWN
}
```  

The power of the `when` expression usage comes with the strong requirement to be exhaustive. Whenever a new value is added to `enum`, we always have to fix compilation issues before pushing our software in production:

```kotlin
fun ContentType.render(content: String): String = when (this) {
    ContentType.PLAIN -> content
    ContentType.MARKDOWN -> {
        val flavour = CommonMarkFlavourDescriptor()
        HtmlGenerator(content, MarkdownParser(flavour).buildMarkdownTreeFromString(content), flavour).generateHtml()
    }
}
```

Now, once we have fixed the `render` method to support the new `ContentType` we can modify `Message` and `MessageVM` extensions method to use the `MARKDOWN` type:
 
```kotlin
fun MessageVM.asDomainObject(contentType: ContentType = ContentType.MARKDOWN): Message = Message(
        content,
        contentType,
        sent,
        user.name,
        user.avatarImageLink.toString(),
        id
)

fun Message.asViewModel(): MessageVM = MessageVM(
        contentType.render(content),
        UserVM(username, URL(userAvatarImageLink)),
        sent,
        id
)
```

Finally, we may modify our tests to ensure that the `MARKDOWN` `contentType` is appropriately rendered. For that purpose, we have to alter the `ChatKotlinApplicationTests.kt` and change the following:
 
 
```kotlin
...
@BeforeEach
fun setUp() {
    ...
            Message(
                    "**testMessage2**",
                    ContentType.MARKDOWN,
                    secondBeforeNow,
                    "test1",
                    "http://test.com"
            ),
            Message(
                    "`testMessage3`",
                    ContentType.MARKDOWN,
                    now,
                    "test2",
                    "http://test.com"
            )
   ...
}

...

@ParameterizedTest
@ValueSource(booleans = [true, false])
fun `test that messages API returns latest messages`(withLastMessageId: Boolean) {
    ...

    assertThat(messages?.map { it.prepareForTesting() })
            .containsSubsequence(
                    MessageVM(
                            "<body><p><strong>testMessage2</strong></p></body>",
                            UserVM("test1", URL("http://test.com")),
                            now.minusSeconds(1).truncatedTo(MILLIS)
                    ),
                    MessageVM(
                            "<body><p><code>testMessage3</code></p></body>",
                            UserVM("test2", URL("http://test.com")),
                            now.truncatedTo(MILLIS)
                    )
            )
}

@Test
fun `test that messages posted to the API are stored`() {
    ...
    messageRepository.findAll()
            .first { it.content.contains("HelloWorld") }
            .apply {
                assertThat(this.prepareForTesting())
                        .isEqualTo(Message(
                                "`HelloWorld`",
                                ContentType.MARKDOWN,
                                now.plusSeconds(1).truncatedTo(MILLIS),
                                "test",
                                "http://test.com"
                        ))
            }
}
```

Once it is done, we will see that all tests are still passing, and the messages with the `MARKDOWN` content type are rendered as expected.
 
During this step, we have seen how to use extensions to improve code quality. We also learned how `when` expression can reduce human error when it comes to adding new business features.
 
Last but not least, our application supports a minimal set of features and has a high-quality codebase. We can finish with these; however, there is a valuable Kotlin feature that improves one vital application characteristic - performance.
 
During the next step, we will improve our app's performance using Kotlin Coroutines and Flow API.
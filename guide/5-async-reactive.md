# Refactoring to Async with Coroutines and to Streaming with Flow

Welcome to the fourth step of our tutorial.

At this step of the tutorial, we will be modifying our code base to bring the coroutines' 
support. Also, at the second part of this step we will be rewriting our services and
controller to replaces pull-based API onto the push-based (a.k.a streaming) API to the
chat application  
 
As the first step, let's make our application fully asynchronous. For that purpose
, Kotlin provides us with the powerful mechanism called [coroutines](https://kotlinlang.org/docs/reference/coroutines/coroutines-guide.html). 

Essentially, coroutines are light-weight threads and allow performing asynchronous
code in the imperative manner. That solves various of [problems](https://stackoverflow.com/a/11632412/4891253) 
related to the callback (observer) pattern used to solve the same problem before. 

> ⚠️ In this tutorial, we will not go too deep into the coroutines and the standard
> kotlinx.coroutines library. To learn more about coroutines and its features, please
> look at the following [tutorial](https://play.kotlinlang.org/hands-on/Introduction%20to%20Coroutines%20and%20Channels/01_Introduction). 

To start using Kotlin coroutines, we have to add one extra library to the `build.gradle.kts`:

```kotlin
dependencies {
    ...
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    ...
}
```

Once the dependency on the coroutines is added, we can start using the main coroutines
 related keyword `suspend`. Using the `suspend` keyword we indicate that the function
 being called is an asynchronous one. Unlike other languages where similar concept is
 exposed via `async`/`await` keywords, the `suspend` function must be handled in the
 coroutine context which can be either another `suspend` function or explicit coroutine
 [`Job`](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/index.html) 
 created via [`CoroutineScope.launch`](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/launch.html)
 or [`runBlocking`](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html) functions.
 
Thus, as the very first step on bring coroutines into the project, we will add
 `suspend` keyword to all controllers and services methods that we have in the project. 
 For example, after the modification the `MessageService` interface should look like
 the following:
 
```kotlin
interface MessageService {

    suspend fun latest(): List<MessageVM>

    suspend fun latestAfter(lastMessageId: String): List<MessageVM>

    suspend fun post(message: MessageVM)
}
```
 
Although in the most cases it is enough to add the `org.jetbrains.kotlinx:kotlinx-coroutines-core`
 dependency, to have proper integration with Spring Framework we need to replace our web
 and database modules of the framework:

<pre>
<code>
dependencies {
    ...
    <s>implementation("org.springframework.boot:spring-boot-starter-web")</s>
    <s>implementation("org.springframework.boot:spring-boot-starter-data-jdbc")</s>
    ...
}
</code>
</pre>

on to the following:

```kotlin 
dependencies {   
    ...
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("io.r2dbc:r2dbc-h2")
    ...
}
```

By adding the above dependencies we replace a standard blocking WebMVC with a fully
 reactive and non-blocking WebFlux. In turn, a blocking JDBC is replaced with a fully
 reactive and non-blocking R2DBC.
 
Thanks to all Spring Framework engineers, migration from Spring WebMVC to Spring
 WebFlux is seamless, and one don`t have to rewrite anything at all! Though, for R2DBC
 we have to do a few extra steps.
 
First, we need to add a configuration file:   
 
```kotlin
@Configuration
class R2dbcConnectionFactoryConfiguration {

    @Bean
    fun initializer(connectionFactory: ConnectionFactory): ConnectionFactoryInitializer {
        val initializer = ConnectionFactoryInitializer()
        initializer.setConnectionFactory(connectionFactory)
        val populator = CompositeDatabasePopulator()
        populator.addPopulators(ResourceDatabasePopulator(ClassPathResource("./sql/schema.sql")))
        initializer.setDatabasePopulator(populator)
        return initializer
    }
}
```

> ⚠️ Please, add the above configuration into the `com/example/kotlin/chat/configuration` folder

The above configuration is a utility one and ensure that the table's schema is
 initialized on the application startup.

The second step should be replacing the previous properties in the `application.properties` onto the following one:
 
```properties
spring.r2dbc.url=r2dbc:h2:file:///./build/data/testdb;USER=sa;PASSWORD=password
```

Once, we have done a few basic configuration related changes, let's do migration from
 Spring Data JDBC to Spring Data R2DBC. To do that, we have to modify the
 `MessageRepository` interface and replace `PagingAndSortingRepository` onto
 `CoroutineCrudRepository`:

```kotlin
interface MessageRepository : CoroutineCrudRepository<Message, String> {

    ...
    fun findLatest(): Flow<Message>

    ...
    fun findLatest(@Param("id") id: String): Flow<Message>
}
```

Note, all methods of the `CoroutineCrudRepository` are designed well for Kotlin
 coroutines. Also, if before we had returned the `List<Messages>` type now we return an
 asynchronous stream of messages exposed through `Flow<Messages>`.
 
> ⚠️ Note, `@Query` annotation now is in the different package, so it should be
> imported as the following:
> ```kotlin
> import org.springframework.data.r2dbc.repository.Query
> ``` 
     
Once the `MessageRepository` is changed we need to fix a few compilation issues in
 the `DefaultMessageService`:
 
```kotlin
@Service
class DefaultMessageService(val messageRepository: MessageRepository) : MessageService {

    override suspend fun latest(): List<MessageVM> =
            messageRepository.findLatest()
                    .map { it.asViewModel() }
                    .toList()

    override suspend fun latestAfter(lastMessageId: String): List<MessageVM> =
            messageRepository.findLatest(lastMessageId)
                    .map { it.asViewModel() }
                    .toList()

   ...
}
```

Note, that the `.map` and `.toList()` in that case are extension functions from the `kotlinx.coroutines.flow`
package, thus they have to be properly imported in the file:

```kotlin
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
```

If you have applied the above changes, it should be enough to maker your application
 asynchronous and non-blocking. Once the ChatApplication is re-run, nothing should be
 changed from the functionality perspective, but now we have all the executions async
 and non-blocking.
 
Finally, we need to apply few more fixes to our tests as well, since now our
 `MessageRepository` is asynchronous, thus we need to run all the related operations in
  the coroutine context as it is shown below:
  
```kotlin
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = [
            "spring.r2dbc.url=r2dbc:h2:mem:///testdb;USER=sa;PASSWORD=password"
        ]
)
class ChatKotlinApplicationTests {
    ...

    @BeforeEach
    fun setUp() = runBlocking {
        ...
    }

    @AfterEach
    fun tearDown() = runBlocking {
        ...
    }

    ...

    @Test
    fun `test that messages posted to the API is stored`(): Unit = runBlocking<Unit> {
        ...
    }
}
```

> ⚠️ Note, in that case the `.first` method extension should be imported from the `kotlinx.coroutines.flow` package 

As we may see from the example above, we can easily fix our tests using `runBlocking
` method which allows performing a coroutine and then wait for its completion. 
 
We can stop at this point, however there is the last option of making our application a
 streaming like. In such a case, frontend will not be polling the server anymore and
 instead the server will be pushing all the new messages to all connected clients. 
 
  
# Web API and Fake Service

Welcome to the first step of our tutorial.

At this step of the tutorial, we will be implementing the first bits of a simple Spring Boot WebMVC application:

 * `HtmlController` - `@Controller` annotated endpoint which will be exposing Html page generated using [Thymeleaf template engine](https://www.thymeleaf.org/doc/tutorials/3.0/thymeleafspring.html)
 * `MessageResource` - `@RestController` annotated endpoint which will be exposing the latest messages as JSON
 * `FakeMessageService` - initial implementation of the `MessageService` interface, which will supply fake data to our chat. For the fake data generation purpose, we will use the [Java Faker](http://dius.github.io/java-faker/) library.
  
As the first step on our road to the chat application, let's put in place the `HttpController` at the `controller` folder. Having generic `@Controller`, we will run the application and see an empty HTML page with no messages. For that purpose, we need to write a normal class and annotate it with the `@Controller` annotation. Also, we need to create a single method that we may call `index()` to show that it serves the main page. The following code written in Kotlin represents the mentioned requirements:
   
```kotlin
@Controller
class HtmlController {

    @GetMapping("/")
    fun index(): String {
        return "chat"
    }
}
```   
Once we run the `ChatKotlinApplication`, and have opened the following URL `http://localhost:8080`, we will see an empty HTML page with the minimalistic markup for our chat.
 
> ⚠️ **Note**, you may wonder why our `HtmlConteroller` is not an open class. The answer is the [`kotlin('plugin.spring')`](https://kotlinlang.org/docs/reference/compiler-plugins.html#all-open-compiler-plugin) plugin, which makes classes annotated with `@Controller`/`@Service`/etc. all open.
 
Of course, an empty chat page looks better than the default 404 error page, but we need some messages on it. To let messages appear on this page, we need to implement the `MessagesService` interface, which is the main business service for our messaging. In the beginning, we implement the fake one and will be generating random fake messages using Shakespeare, Yoda, and Rick&Morty famous quotes:
 
```kotlin
@Service
class FakeMessageService : MessageService {

    val users: Map<String, UserVM> = mapOf(
        "Shakespeare"  to UserVM("Shakespeare", URL("https://blog.12min.com/wp-content/uploads/2018/05/27d-William-Shakespeare.jpg")),
        "RickAndMorty" to UserVM("RickAndMorty", URL("http://thecircular.org/wp-content/uploads/2015/04/rick-and-morty-fb-pic1.jpg")),
        "Yoda"         to UserVM("Yoda", URL("https://news.toyark.com/wp-content/uploads/sites/4/2019/03/SH-Figuarts-Yoda-001.jpg"))
    )

    val usersQuotes: Map<String, () -> String> = mapOf(
        "Shakespeare"  to { Faker.instance().shakespeare().asYouLikeItQuote() },
        "RickAndMorty" to { Faker.instance().rickAndMorty().quote() },
        "Yoda"         to { Faker.instance().yoda().quote() }
    )

    override fun latest(): List<MessageVM> {
        val count = Random.nextInt(1, 15)
        return (0..count).map {
            val user = users.values.random()
            val userQuote = usersQuotes.getValue(user.name).invoke()

            MessageVM(userQuote, user, Instant.now(), Random.nextBytes(10).toString())
        }.toList()
    }

    override fun latestAfter(lastMessageId: String): List<MessageVM> {
        return latest()
    }

    override fun post(message: MessageVM) {
        TODO("Not yet implemented")
    }
}
```

> ⚠️ Please put your `FakeMessageService` at the `service/impl` folder to follow the general project structure.  

As we might see from the implementation example above, Kotlin provides powerful support from the language syntax perspective. For example, we do not have to implement all the methods immediately. Thus, missing implementations can be safely replaced with the `TODO()` function. 
 
> 💡 The `TODO()` function plays two roles: the reminder role as well as it always throws `NotImplementedError` exception, so it plays the stab role.  

Another notable benefit is the fluent collection API. We all might remember those challenges related to building Map with predefined entries. With Kotlin, it becomes beautiful simplicity: `mapOf` function let you create a map of `Pair's, where the pair definition is provided with an [extension](https://kotlinlang.org/docs/reference/extensions.html) method `<A, B> A.to(that: B): Pair<A, B>`.
  
> ✋ Do not worry if you have no experience with Kotlin extensions. We will learn more about them in part 3 of this tutorial.

As the following step, we need to generate a random number of messages to be delivered. This case leveraging the power of the Kotlin language as well. To generate an `IntRange` we can simply say `(0..count)` and then apply `.map` to transform it into a messages sequence. Notably, the selection of a random element from any collection can be made as easy as pie. Kotlin provides an extension method for collections, which is called `.random()`. This extension method does everything for you to select and return an item from the source.
  
Also, in this example, you may see the `MessageVM` data structure. This data structure is a view model used to deliver data to a client. The data structure is simplified to provide the most essential information to a browser:
 
```kotlin
data class MessageVM(val content: String, val user: UserVM, val sent: Instant, val id: String? = null)
```

You may note that in our case, we use the prefix `data` with `class`, which automatically generates `toString`, `equals`, and `hashCode` for you, so you do not need to take care of that anymore.
 
To deliver messages to the UI, we have to add `latest` messages to the Chat view context, so the template engine will render them properly. For that purpose, we have to modify `HtmlController` and configure the given request `Model` as it is shown in the following example:    
 
 
```kotlin
@Controller
class HtmlController(val messageService: MessageService) {

    @GetMapping("/")
    fun index(model: Model): String {
        val messages = messageService.latest()

        model["messages"] = messages

        return "chat"
    }
}
```

> 💡 Spring Web users may notice that `Model` is used in this example as a `Map` even though it does not extend this API. This becomes possible with another Kotlin extension, which provides overloading for the `set` operator. For more information, please see [operator overloading](https://kotlinlang.org/docs/reference/operator-overloading.html) docs.  

One the application is restarted, we will see a bunch of messages on the screen. 

Finally, to see new update messages, wee need to add the final piece into our API, which will be used for our client-side polling mechanism. 
 
First of all, let's have a short description of the client-side polling mechanic. The implementation is a straightforward one and uses pagination principles to get new messages. That said, we need to know `latestMessageId` to receive new messages, which came after. Thus, at the page rendering phase, along with just messages, we have to populate the request `Model` with the `latestMessageId` property:
 
```kotlin
@GetMapping("/")
fun index(model: Model): String {
    val messages: List<MessageVM> = messageService.latest()

    model["messages"] = messages
    model["lastMessageId"] = messages.lastOrNull()?.id ?: ""

    return "chat"
}
```

Then, we have to create an API endpoint, which going to serve polling requests: 
```kotlin
@RestController
@RequestMapping("/api/v1/messages")
class MessageResource(val messageService: MessageService) {

    @GetMapping
    fun latest(@RequestParam(value = "lastMessageId", defaultValue = "") lastMessageId: String): ResponseEntity<List<MessageVM>> {
        val messages = if (lastMessageId.isNotEmpty()) {
            messageService.latestAfter(lastMessageId)
        } else {
            messageService.latest()
        }

        return if (messages.isEmpty()) {
            with(ResponseEntity.noContent()) {
                header("lastMessageId", lastMessageId)
                build<List<MessageVM>>()
            }
        } else {
            with(ResponseEntity.ok()) {
                header("lastMessageId", messages.last().id)
                body(messages)
            }
        }
    }

    @PostMapping
    fun post(@RequestBody message: MessageVM) {
        messageService.post(message)
    }
}
```

> ⚠️ Note, `MessageResource` should be placed at the same folder as the `HtmlController`.

Here, depends on the presence of the `lastMessageId` query parameter, we will be calling different service methods to server all the latest messages, if non were displayed before, or the latest messages after the specific message-id.
 
Once the mentioned code is implemented, and the application is restarted, we will see a continuously updated conversation between Shakespeare, Yoda, and Rick&Morty.
 
During this step, we have implemented the essential parts of our Chat app. We can see messages on the screen, and the polling mechanism can retrieve new messages from the `@RestController` endpoint. We learned the power of Kotlin and have seen how Kotlin extensions and language syntax improve readability and decrease the amount of code we have to write. Simultaneously, it does not increase complexity since Kotlin is as simple as Java and does not introduce complexity that we may face while using functional languages).   

> Functional languages may decrease the amount of code you write but can significantly increase the codebase's general complexity.
     
In the following step, we will see how to integrate our application with a real database and make messaging persistable.
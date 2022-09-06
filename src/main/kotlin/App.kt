import dev.inmo.micro_utils.repos.cache.cache.FullKVCache
import dev.inmo.micro_utils.repos.cache.cached
import dev.inmo.micro_utils.repos.exposed.keyvalue.ExposedKeyValueRepo
import dev.inmo.micro_utils.repos.mappers.withMapper
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.ChatId
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import models.Config

/**
 * This method by default expects one argument in [args] field: telegram bot configuration
 */
suspend fun main(args: Array<String>) {
    // create json to decode config
    val json = Json { ignoreUnknownKeys = true }
    // decode config
    val config: Config = json.decodeFromString(Config.serializer(), File(args.first()).readText())
    // that is your bot
    val bot = telegramBot(config.token)

    // that is kotlin coroutine scope which will be used in requests and parallel works under the hood
    val scope = CoroutineScope(Dispatchers.Default)

    val repo = ExposedKeyValueRepo(
        config.database.database,
        { long("chat_id") },
        { text("config") },
        "configs"
    ).withMapper(
        { chatId },
        { json.encodeToString(ChatConfig.serializer(), this) },
        { ChatId(this) },
        { json.decodeFromString(ChatConfig.serializer(), this) },
    ).cached(FullKVCache(), scope = scope)

    val chatsChangingMutex = Mutex()
    val chatsSendingJobs = mutableMapOf<ChatId, Job>()


    // here should be main logic of your bot
    bot.buildBehaviourWithLongPolling(scope) {
        // in this lambda you will be able to call methods without "bot." prefix
        val me = getMe()

        // this method will create point to react on each /start command
        onCommand("start", requireOnlyCommandInMessage = true) {
            // simply reply :)
            reply(it, "Hello, I am ${me.firstName}")
        }

        // That will be called on the end of bot initiation. After that println will be started long polling and bot will
        // react on your commands
        println(me)
    }.join()
}

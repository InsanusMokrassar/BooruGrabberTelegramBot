import dev.inmo.krontab.utils.asFlow
import dev.inmo.micro_utils.coroutines.*
import dev.inmo.micro_utils.pagination.utils.doForAllWithNextPaging
import dev.inmo.micro_utils.repos.add
import dev.inmo.micro_utils.repos.cache.cache.FullKVCache
import dev.inmo.micro_utils.repos.cache.cached
import dev.inmo.micro_utils.repos.exposed.keyvalue.ExposedKeyValueRepo
import dev.inmo.micro_utils.repos.exposed.onetomany.ExposedKeyValuesRepo
import dev.inmo.micro_utils.repos.mappers.withMapper
import dev.inmo.micro_utils.repos.unset
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.delete
import dev.inmo.tgbotapi.extensions.api.send.media.*
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.requests.abstracts.FileUrl
import dev.inmo.tgbotapi.types.*
import dev.inmo.tgbotapi.types.chat.ChannelChat
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.media.TelegramMediaPhoto
import dev.inmo.tgbotapi.types.message.content.PhotoContent
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import models.Config
import net.kodehawa.lib.imageboards.ImageBoard
import net.kodehawa.lib.imageboards.entities.BoardImage

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

    ImageBoard.setUserAgent("WhoAmI?")

    // that is kotlin coroutine scope which will be used in requests and parallel works under the hood
    val scope = CoroutineScope(Dispatchers.Default + ContextSafelyExceptionHandler { it.printStackTrace() })

    val repo = ExposedKeyValueRepo(
        config.database.database,
        { long("chat_id") },
        { text("config") },
        "configs"
    ).withMapper(
        { chatId },
        { json.encodeToString(ChatSettings.serializer(), this) },
        { ChatId(this) },
        { json.decodeFromString(ChatSettings.serializer(), this) },
    ).cached(FullKVCache(), scope = scope)

    val chatsUrlsSeen = ExposedKeyValuesRepo(
        config.database.database,
        { long("chat_id") },
        { text("url") },
        "chatsUrlsSeen"
    ).withMapper(
        { chatId },
        { this },
        { ChatId(this) },
        { this },
    ).cached(FullKVCache(), scope = scope)

    val chatsChangingMutex = Mutex()
    val chatsSendingJobs = mutableMapOf<ChatId, Job>()

    // here should be main logic of your bot
    bot.buildBehaviourWithLongPolling(scope) {
        // in this lambda you will be able to call methods without "bot." prefix
        val me = getMe()

        suspend fun refreshChatJob(chatId: ChatId, settings: ChatSettings?) {
            val settings = settings ?: repo.get(chatId)
            chatsChangingMutex.withLock {
                chatsSendingJobs[chatId] ?.cancel()
                settings ?.let {
                    chatsSendingJobs[chatId] = settings.scheduler.asFlow().subscribeSafelyWithoutExceptions(scope) {
                        val result = mutableListOf<BoardImage>()
                        let {
                            var i = 0
                            while (result.size < settings.count) {
                                val images = settings.makeRequest(i).takeIf { it.isNotEmpty() } ?: break
                                result.addAll(
                                    images.filterNot {
                                        chatsUrlsSeen.contains(chatId, it.url)
                                    }
                                )
                                i++
                            }
                        }
                        when {
                            result.isEmpty() -> return@subscribeSafelyWithoutExceptions
                            result.size == 1 -> sendPhoto(
                                chatId,
                                FileUrl(result.first().url)
                            ).also {
                                result.forEach { chatsUrlsSeen.add(chatId, it.url) }
                            }
                            settings.gallery -> result.chunked(mediaCountInMediaGroup.last + 1).forEach {
                                sendVisualMediaGroup(
                                    chatId,
                                    it.map {
                                        TelegramMediaPhoto(FileUrl(it.url))
                                    }
                                ).also { _ ->
                                    it.forEach { chatsUrlsSeen.add(chatId, it.url) }
                                }
                            }
                            else -> result.forEach {
                                sendPhoto(
                                    chatId,
                                    FileUrl(it.url)
                                ).also { _ ->
                                    chatsUrlsSeen.add(chatId, it.url)
                                }
                            }
                        }
                    }
                }
            }
        }

        doForAllWithNextPaging {
            repo.keys(it).also {
                it.results.forEach {
                    runCatchingSafely {
                        refreshChatJob(it, null)
                    }
                }
            }
        }

        repo.onNewValue.subscribeSafelyWithoutExceptions(this) {
            refreshChatJob(it.first, it.second)
        }
        repo.onValueRemoved.subscribeSafelyWithoutExceptions(this) {
            refreshChatJob(it, null)
        }

        onCommand(Regex("(help|start)"), requireOnlyCommandInMessage = true) {
            reply(it, EnableArgsParser(it.chat.id, repo, scope).getFormattedHelp().takeIf { it.isNotBlank() } ?: return@onCommand)
        }
        onCommand("enable", requireOnlyCommandInMessage = false) {
            val args = it.content.textSources.drop(1).joinToString("") { it.source }.split(" ")
            val parser = EnableArgsParser(it.chat.id, repo, this)
            runCatchingSafely {
                parser.parse(args)
            }.onFailure { e ->
                e.printStackTrace()
                if (it.chat is PrivateChat) {
                    reply(it, parser.getFormattedHelp())
                }
            }
            runCatchingSafely {
                if (it.chat is ChannelChat) {
                    delete(it)
                }
            }
        }
        onCommand("disable", requireOnlyCommandInMessage = true) {
            runCatchingSafely {
                repo.unset(it.chat.id)
            }
            runCatchingSafely {
                delete(it)
            }
        }

        setMyCommands(
            listOf(
                BotCommand("start", "Will return the help for the enable command"),
                BotCommand("help", "Will return the help for the enable command"),
                BotCommand("enable", "Will enable images grabbing for current chat or update exists settings"),
                BotCommand("disable", "Will disable bot for current chat"),
            )
        )

        println(me)
    }.join()
}

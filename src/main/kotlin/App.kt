import dev.inmo.krontab.utils.asFlowWithDelays
import dev.inmo.micro_utils.coroutines.*
import dev.inmo.micro_utils.pagination.utils.doForAllWithNextPaging
import dev.inmo.micro_utils.repos.*
import dev.inmo.micro_utils.repos.cache.full.fullyCached
import dev.inmo.micro_utils.repos.exposed.keyvalue.ExposedKeyValueRepo
import dev.inmo.micro_utils.repos.exposed.onetomany.ExposedKeyValuesRepo
import dev.inmo.micro_utils.repos.mappers.withMapper
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
import dev.inmo.tgbotapi.utils.code
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import models.Config
import net.kodehawa.lib.imageboards.ImageBoard
import net.kodehawa.lib.imageboards.boards.DefaultBoards
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
    ).fullyCached(scope = scope)

    val chatsUrlsSeenRepo = ExposedKeyValuesRepo(
        config.database.database,
        { long("chat_id") },
        { text("url") },
        "chatsUrlsSeen"
    ).withMapper(
        { chatId },
        { this },
        { ChatId(this) },
        { this },
    )

    val chatsChangingMutex = Mutex()
    val chatsSendingJobs = mutableMapOf<ChatId, Job>()

    // here should be main logic of your bot
    bot.buildBehaviourWithLongPolling(scope) {
        // in this lambda you will be able to call methods without "bot." prefix
        val me = getMe()

        suspend fun triggerSendForChat(chatId: ChatId, settings: ChatSettings) {
            val seenUrls = chatsUrlsSeenRepo.getAll(chatId).toMutableSet()
            val result = let {
                val result = mutableListOf<BoardImage>()
                var i = 0
                while (result.size < settings.count) {
                    val images = settings.makeRequest(i).takeIf { it.isNotEmpty() } ?: break
                    result.addAll(
                        images.filterNot {
                            seenUrls.contains(it.url ?: return@filterNot true)
                        }
                    )
                    i++
                }
                result.take(settings.count)
            }.takeIf { it.isNotEmpty() } ?: return
            runCatchingSafely {
                val urls = result.map { it.url }
                chatsUrlsSeenRepo.add(chatId, urls)
                seenUrls.addAll(urls)
                when {
                    urls.isEmpty() -> return@runCatchingSafely
                    urls.size == 1 -> sendPhoto(
                        chatId,
                        FileUrl(urls.first()),
                        if (settings.attachUrls) urls.first() else null
                    )
                    settings.gallery -> urls.chunked(mediaCountInMediaGroup.last + 1).forEach {
                        sendVisualMediaGroup(
                            chatId,
                            it.map {
                                TelegramMediaPhoto(FileUrl(it), if (settings.attachUrls) it else null)
                            }
                        )
                    }
                    else -> urls.forEach {
                        sendPhoto(
                            chatId,
                            FileUrl(it),
                            if (settings.attachUrls) it else null
                        )
                    }
                }
            }.onFailure {
                triggerSendForChat(chatId, settings)
            }
        }

        suspend fun refreshChatJob(chatId: ChatId, settings: ChatSettings?) {
            val settings = settings ?: repo.get(chatId)
            chatsChangingMutex.withLock {
                chatsSendingJobs[chatId] ?.cancel()
                settings ?.scheduler ?.let {
                    chatsSendingJobs[chatId] = it.asFlowWithDelays().subscribeSafelyWithoutExceptions(scope) {
                        triggerSendForChat(chatId, settings)
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

        doForAllWithNextPaging {
            repo.keys(it).also {
                it.results.forEach {
                    runCatchingSafely {
                        refreshChatJob(it, null)
                    }
                }
            }
        }

        onCommand(Regex("(help|start)"), requireOnlyCommandInMessage = true) {
            reply(it, EnableArgsParser().getFormattedHelp() ?.takeIf { it.isNotBlank() } ?: return@onCommand)
        }
        onCommand("enable", requireOnlyCommandInMessage = false) {
            val args = it.content.textSources.drop(1).joinToString("") { it.source }.split(" ")
            val parser = EnableArgsParser()
            runCatchingSafely {
                parser.parse(args)
                repo.set(ChatId(it.chat.id.chatId), parser.resultSettings ?: return@runCatchingSafely)
            }.onFailure { e ->
                e.printStackTrace()
                if (it.chat is PrivateChat) {
                    reply(it, parser.getFormattedHelp()!!)
                }
            }
            runCatchingSafely {
                if (it.chat is ChannelChat) {
                    delete(it)
                }
            }
        }
        onCommand("request", requireOnlyCommandInMessage = false) {
            val args = it.content.textSources.drop(1).joinToString("") { it.source }.trim().takeIf { it.isNotBlank() } ?.split(" ")

            val chatSettings = if (args.isNullOrEmpty()) {
                repo.get(ChatId(it.chat.id.chatId)) ?: run {
                    if (it.chat is PrivateChat) {
                        reply(it, "Unable to find default config")
                    }
                    return@onCommand
                }
            } else {
                val parser = EnableArgsParser(repo.get(ChatId(it.chat.id.chatId)) ?: ChatSettings.DEFAULT)
                runCatchingSafely {
                    parser.parse(args)
                    parser.resultSettings
                }.onFailure { e ->
                    e.printStackTrace()
                    if (it.chat is PrivateChat) {
                        reply(it, parser.getFormattedHelp()!!)
                    }
                }.getOrNull()
            }

            triggerSendForChat(ChatId(it.chat.id.chatId), chatSettings ?: return@onCommand)
        }
        onCommand("disable", requireOnlyCommandInMessage = true) {
            runCatchingSafely {
                repo.unset(ChatId(it.chat.id.chatId))
            }
            runCatchingSafely {
                delete(it)
            }
        }
        onCommand("take_settings", requireOnlyCommandInMessage = true) {
            val settings = runCatchingSafely {
                repo.get(ChatId(it.chat.id.chatId))
            }.getOrNull()
            runCatchingSafely {
                if (settings == null) {
                    reply(it, "You didn't enable requesting")
                } else {
                    reply(it, ) {
                        +"Query: " + code(settings.query) + "\n"
                        +"Krontab: " + code(settings.krontabTemplate ?: "unset") + "\n"
                        +"Board: " + code(DefaultBoards.values().first { it == settings.board.boardType }.name) + "\n"
                        +"Count: " + code(settings.count.toString()) + "\n"
                        +"Gallery: " + code(settings.gallery.toString()) + "\n"
                        +"Rating: " + code(settings.rating ?.name ?: "unset") + "\n"
                        +"Attach urls: " + code(settings.attachUrls.toString()) + "\n"
                        +"Command: " + code(
                            "/request " +
                                "${settings.query} " +
                                (settings.krontabTemplate ?.let { "-k $it " } ?: "") +
                                "-b ${DefaultBoards.values().first { it == settings.board.boardType }.name.lowercase()} " +
                                "-n ${settings.count} " +
                                (if (settings.gallery) "-g " else "") +
                                (settings.rating ?.let { "-r ${it.name} " } ?: "") +
                                (if (settings.attachUrls) "-a " else "")

                        )
                    }
                }
            }
        }

        setMyCommands(
            listOf(
                BotCommand("start", "Will return the help for the enable command"),
                BotCommand("help", "Will return the help for the enable command"),
                BotCommand("request", "Will trigger image immediately with custom settings from arguments or default settings of chat if any"),
                BotCommand("enable", "Will enable images grabbing for current chat or update exists settings"),
                BotCommand("disable", "Will disable bot for current chat"),
                BotCommand("take_settings", "Take your current settings"),
            )
        )

        println(me)
    }.join()
}

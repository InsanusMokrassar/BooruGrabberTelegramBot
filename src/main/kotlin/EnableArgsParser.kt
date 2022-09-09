import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import dev.inmo.micro_utils.coroutines.launchSafelyWithoutExceptions
import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.micro_utils.repos.set
import dev.inmo.tgbotapi.types.ChatId
import kotlinx.coroutines.CoroutineScope
import net.kodehawa.lib.imageboards.entities.Rating

class EnableArgsParser: CliktCommand(name = "enable") {
    val count by option("-n").int().help("Amount of pictures to grab each trigger time").default(1).check("Count should be in range 1-10") {
        it in 1 .. 10
    }
    val query by argument().multiple(required = true).help("Your query to booru. Use syntax \"-- -sometag\" to add excluding of some tag in query")
    val krontab by option("-k", "--krontab").transformValues(5) {
        it.joinToString(" ")
    }.help("Krontab in format * * * * *. See https://bookstack.inmo.dev/books/krontab/page/string-format")
    val board by option("-b", "--board").convert {
        ChatSettings.BoardSerializer.types.getValue(it)
    }.required().help("Board type. Possible values: ${ChatSettings.BoardSerializer.types.keys.joinToString { it }}")
    val gallery by option("-g", "--gallery").flag(default = false).help("Effective only when count passed > 1. Will send chosen images as gallery instead of separated images")
    val rating by option("-r", "--rating").enum<Rating> { it.name.lowercase() }
    val attachUrls by option("-a", "--attach_urls").flag(default = false)

    var resultSettings: ChatSettings? = null
        private set

    override fun run() {
        resultSettings = ChatSettings(
            query.filterNot { it.isEmpty() }.joinToString(" ").trim(),
            krontab,
            board,
            count,
            gallery,
            rating,
            attachUrls
        )
    }
}

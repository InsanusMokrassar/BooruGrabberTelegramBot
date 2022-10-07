import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import net.kodehawa.lib.imageboards.boards.DefaultBoards
import net.kodehawa.lib.imageboards.entities.Rating

class EnableArgsParser(
    onlyQueryIsRequired: Boolean,
    private val base: ChatSettings = ChatSettings("", null, DefaultBoards.SAFEBOORU)
) : CliktCommand(name = "enable") {
    private fun <EachT : Any, ValueT> NullableOption<EachT, ValueT>.default(
        value: EachT?,
        defaultForHelp: String = value.toString(),
    ): NullableOption<EachT, ValueT> {
        return if (value != null) {
            transformAll(defaultForHelp) { it.lastOrNull() ?: value }
        } else {
            this
        }
    }
    val count by option("-n")
        .int()
        .help("Amount of pictures to grab each trigger time")
        .check("Count should be in range 1-10") { it in 1 .. 10 }
    val query by argument()
        .multiple(required = true)
        .help("Your query to booru. Use syntax \"-- -sometag\" to add excluding of some tag in query")
    val krontab by option("-k", "--krontab")
        .transformValues(5) { it.joinToString(" ") }
        .help("Krontab in format * * * * *. See https://bookstack.inmo.dev/books/krontab/page/string-format")
    val board by option("-b", "--board")
        .convert { ChatSettings.BoardSerializer.types.getValue(it) }
        .run {
            if (onlyQueryIsRequired) {
                this
            } else {
                required()
            }
        }
        .help("Board type. Possible values: ${ChatSettings.BoardSerializer.types.keys.joinToString { it }}")
    val gallery by option("-g", "--gallery")
        .flag(default = base.gallery)
        .help("Effective only when count passed > 1. Will send chosen images as gallery instead of separated images")
    val rating by option("-r", "--rating")
        .enum<Rating> { it.name.lowercase() }
    val attachUrls by option("-a", "--attach_urls")
        .flag(default = base.attachUrls)

    var resultSettings: ChatSettings? = null
        private set

    override fun run() {
        resultSettings = ChatSettings(
            query.filterNot { it.isEmpty() }.joinToString(" ").trim(),
            krontab,
            board ?: base.board.boardType as DefaultBoards,
            count ?: base.count,
            gallery,
            rating,
            attachUrls
        )
    }
}

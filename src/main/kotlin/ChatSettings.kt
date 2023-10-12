import dev.inmo.krontab.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.kodehawa.lib.imageboards.ImageBoard
import net.kodehawa.lib.imageboards.boards.DefaultBoards
import net.kodehawa.lib.imageboards.entities.BoardImage
import net.kodehawa.lib.imageboards.entities.Rating

@Serializable
data class ChatSettings(
    val query: String,
    val krontabTemplate: KrontabTemplate? = null,
    @Serializable(BoardSerializer::class)
    private val boardBase: DefaultBoards = DefaultBoards.SAFEBOORU,
    val count: Int = 1,
    val gallery: Boolean = false,
    val rating: Rating? = null,
    val attachUrls: Boolean = false
) {
    val scheduler by lazy {
        krontabTemplate ?.toSchedule()
    }

    val board: ImageBoard<*>
        get() = when (boardBase) {
            DefaultBoards.R34 -> InternalBoards.RULE34
            DefaultBoards.E621 -> InternalBoards.E621
            DefaultBoards.KONACHAN -> InternalBoards.KONACHAN
            DefaultBoards.YANDERE -> InternalBoards.YANDERE
            DefaultBoards.DANBOORU -> InternalBoards.DANBOORU
            DefaultBoards.SAFEBOORU -> InternalBoards.SAFEBOORU
            DefaultBoards.GELBOORU -> InternalBoards.GELBOORU
            DefaultBoards.E926 -> InternalBoards.E926
        }

    suspend fun makeRequest(page: Int): List<BoardImage> {
        return withContext(Dispatchers.IO) {
            board.search(page, count, query, rating).blocking() ?: emptyList()
        }
    }

    @Serializer(DefaultBoards::class)
    object BoardSerializer : KSerializer<DefaultBoards> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor
        val types = DefaultBoards.values().associateBy { it.name.lowercase() }
        override fun deserialize(decoder: Decoder): DefaultBoards {
            val type = decoder.decodeString()
            return types.getValue(type)
        }

        override fun serialize(encoder: Encoder, value: DefaultBoards) {
            encoder.encodeString(value.name.lowercase())
        }
    }

    companion object {
        val DEFAULT = ChatSettings("", null, DefaultBoards.SAFEBOORU)
    }
}

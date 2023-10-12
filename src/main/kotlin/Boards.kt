import net.kodehawa.lib.imageboards.ImageBoard
import net.kodehawa.lib.imageboards.boards.DefaultBoards
import net.kodehawa.lib.imageboards.entities.impl.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


class Boards(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
) {
    val E621 = ImageBoard(client, DefaultBoards.E621, FurryImage::class.java)
    val KONACHAN = ImageBoard(client, DefaultBoards.KONACHAN, KonachanImage::class.java)
    val RULE34 = ImageBoard(client, DefaultBoards.R34, Rule34Image::class.java)
    val YANDERE = ImageBoard(client, DefaultBoards.YANDERE, YandereImage::class.java)
    val DANBOORU = ImageBoard(client, DefaultBoards.DANBOORU, DanbooruImage::class.java)
    val SAFEBOORU = ImageBoard(
        client, DefaultBoards.SAFEBOORU,
        SafebooruImage::class.java
    )
    val E926 = ImageBoard(client, DefaultBoards.E926, SafeFurryImage::class.java)
    val GELBOORU = ImageBoard(client, DefaultBoards.GELBOORU, GelbooruImage::class.java)
}
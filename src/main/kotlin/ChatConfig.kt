import dev.inmo.krontab.*
import kotlinx.serialization.Serializable

@Serializable
data class ChatConfig(
    val krontab: KrontabTemplate
) {
    val scheduler by lazy {
        krontab.toSchedule()
    }
}

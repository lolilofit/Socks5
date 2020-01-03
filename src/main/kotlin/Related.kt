import java.nio.ByteBuffer
import java.nio.channels.SelectionKey

data class Related(
        var relatedChanel : SelectionKey? = null,
        var isClient : Boolean,
        var countMes : Int,
        var writeThis : ByteBuffer,
        var readThis : ByteBuffer,
        val connectionNum : Int
)

import java.nio.ByteBuffer
import java.nio.channels.SelectionKey

data class Related(
        var relatedChanel : SelectionKey? = null,
        var isClient : Boolean,
        var countMes : Int,
        var writeThis : ByteBuffer,
        var readThis : ByteBuffer,
        var readedNum : Int = 0,
        val connectionNum : Int
)

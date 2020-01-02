import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

var cnt = 0

 class Proxy  {
    val pars = Parse()

    private fun readFromChanel(key : SelectionKey) {

        val client = key.channel() as SocketChannel
        var related : Related? = key.attachment() as Related?
        if(related == null) {
            key.attach(Related(null, isClient = true, countMes = 0,
                    writeThis = ByteBuffer.allocate(1024), readThis = ByteBuffer.allocate(1024), connectionNum = cnt))
            cnt++
            related = if(key.attachment() is Related) key.attachment() as Related else null
        }
        val c = related?.connectionNum
        println("$c read")
        var readed = 0

        try {
            related?.readThis?.clear()
            readed = client.read(related?.readThis)
            if (readed < 1) {
                pars.closeChanel(key)
                return
            }
            related?.readedNum = readed
            println("readed $readed")
            if(readed > 10)
                print("")
        }
        catch (e : IOException) {
            pars.closeChanel(key)
            e.printStackTrace()
            return
        }

        if(related != null && related.countMes == 0 && related.isClient) {
            pars.firstMessage(key, related)
            return
        }
        if(related?.countMes == 1 && related.isClient) {
            pars.openRemoteConnection(key, related)
            return
        }
        pars.transfer(key, related)

    }

        fun run(port : Int) {

        val socketChanel : ServerSocketChannel = ServerSocketChannel.open()
        socketChanel.configureBlocking(false)
        val socket = socketChanel.socket()
        socket.bind(InetSocketAddress(port))
        val selector = Selector.open()
        socketChanel.register(selector, SelectionKey.OP_ACCEPT)


        while(true) {
            val ready = selector.select()
            if(ready == 0) continue
            val keys = selector.selectedKeys()
            val cur = keys.iterator()
            while(cur.hasNext()) {
                val oneKey = cur.next() as SelectionKey
                cur.remove()
                if(!oneKey.isValid) {
                    continue
                }
                if(oneKey.isAcceptable) {
                    val newSocket = socket.accept()
                    val newChanel = newSocket.channel
                    if(newChanel != null) {
                        newChanel.configureBlocking(false)
                        newChanel.register(selector, SelectionKey.OP_READ)
                    }
                }
                if(oneKey.isReadable)
                    readFromChanel(oneKey)
                if(!oneKey.isValid) {
                    continue
                }
                if(oneKey.isWritable) {
                    val related = oneKey.attachment() as Related
                    if(related.countMes == 2)
                        pars.thirdMessage(oneKey, related)
                    else
                        pars.write(oneKey)
                }
                if(!oneKey.isValid) {
                    continue
                }
                if(oneKey.isConnectable)
                    pars.connect(oneKey)
            }
        }
    }
}

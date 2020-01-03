import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel


 class Proxy  {
    private val proxyOperations = ProxyOperations()

     fun run(port : Int) {

        val socketChanel : ServerSocketChannel = ServerSocketChannel.open()
        socketChanel.configureBlocking(false)
        val socket = socketChanel.socket()
        socket.bind(InetSocketAddress(port))
        val selector = Selector.open()
        socketChanel.register(selector, SelectionKey.OP_ACCEPT)

        val dnsChannel = proxyOperations.createDatagramChannel();
         if(dnsChannel != null)
             dnsChannel.register(selector, SelectionKey.OP_READ);


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
                    proxyOperations.readFromChanel(oneKey)
                if(!oneKey.isValid) {
                    continue
                }
                if(oneKey.isWritable) {
                    val related = oneKey.attachment() as Related
                    if(related.countMes == 2)
                        proxyOperations.thirdMessage(oneKey, related)
                    else
                        proxyOperations.write(oneKey)
                }
                if(!oneKey.isValid) {
                    continue
                }
                if(oneKey.isConnectable)
                    proxyOperations.connect(oneKey)
            }
        }
    }
}

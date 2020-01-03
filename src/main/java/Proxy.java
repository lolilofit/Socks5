
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;


public class Proxy {
    private ProxyOperations proxyOperations = new ProxyOperations();

    void run(int port) {
        Selector selector;
        ServerSocket socket;
        try {
            ServerSocketChannel socketChanel = ServerSocketChannel.open();
            socketChanel.configureBlocking(false);
            socket = socketChanel.socket();
            socket.bind(new InetSocketAddress(port));
            selector = Selector.open();
            socketChanel.register(selector, SelectionKey.OP_ACCEPT);
            DatagramChannel dnsChannel = proxyOperations.createDatagramChannel();
            if (dnsChannel != null)
                dnsChannel.register(selector, SelectionKey.OP_READ);

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        while (true) {
            int ready = 0;
            try {
                ready = selector.select();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            if (ready == 0)
                continue;
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> cur = keys.iterator();
            while (cur.hasNext()) {
                SelectionKey oneKey = cur.next();
                cur.remove();
                try {
                    if (oneKey.isValid() && oneKey.isAcceptable()) {
                        Socket newSocket;
                        newSocket = socket.accept();
                        SocketChannel newChanel = newSocket.getChannel();
                        if (newChanel != null) {
                            newChanel.configureBlocking(false);
                            newChanel.register(selector, SelectionKey.OP_READ);
                        }
                    }
                    else {
                        if (oneKey.isValid() && oneKey.isReadable())
                            proxyOperations.readFromChanel(oneKey);
                        else {
                            if (oneKey.isValid() && oneKey.isWritable()) {
                                Related related = (Related) oneKey.attachment();
                                if (related.getCountMes() == 2)
                                    proxyOperations.thirdMessage(oneKey, related);
                                else
                                    proxyOperations.write(oneKey);
                            } else {
                                if (oneKey.isValid() && oneKey.isConnectable())
                                    proxyOperations.connect(oneKey);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    oneKey.interestOps(0);
                }
            }
        }
    }
}

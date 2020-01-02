import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;


public class Parse {
    void transfer(SelectionKey key, Related related) {
        System.out.println(related.getConnectionNum() + " transfer ");
        related.getReadThis().flip();

/*
        ByteBuffer buf = ByteBuffer.allocate(related.getReadedNum());
        byte[] writeThis = new byte[related.getReadedNum()];
        System.arraycopy(related.getReadThis().array(), 0, writeThis, 0, related.getReadedNum());
        buf.put(writeThis);
        //buf.rewind();
        buf.flip();
*/
        Related relatedCli = (Related) related.getRelatedChanel().attachment();
        if(relatedCli != null) {
            relatedCli.setWriteThis(related.getReadThis());
            relatedCli.setReadedNum(related.getReadedNum());
        }

        related.getRelatedChanel().interestOps(SelectionKey.OP_WRITE);
        key.interestOps(0);
        related.setReadedNum(0);

    }

    void openRemoteConnection(SelectionKey key, Related related) throws IOException {
        byte[] array  = related.getReadThis().array();
        if(array[1] != 1 || array[9] == 0) {
            if(array[1] != 1)
                System.out.println(related.getConnectionNum() + " DOMAIN NAME!!");
            return;
        }

        if(array[3] != 1) {System.out.println(related.getConnectionNum() + " I can't work with thiss type of addr"); return;}

        int[] addr = new int[] { 0xFF & array[4], 0xFF & array[5], 0xFF & array[6], 0xFF & array[7] };
        String ipAdr = addr[0] + "." + addr[1] + "." + addr[2] + "." + addr[3];
        int intPort = (0xFF&array[8])*256 + 0xFF&array[9];

        System.out.println(related.getConnectionNum() + " create connection with:" + ipAdr + " port: " + intPort );

        SocketChannel hostConnection = SocketChannel.open();
        hostConnection.configureBlocking(false);

        boolean flag = false;
        byte[] addrToCon = {array[4], array[5], array[6], array[7]};
        InetAddress a = InetAddress.getByAddress(addrToCon);
        if(!hostConnection.connect(new InetSocketAddress(a, intPort))) {
            System.out.println(related.getConnectionNum() + " can't connect now");
            flag = true;
        }
        SelectionKey hostKey = hostConnection.register(key.selector(), SelectionKey.OP_READ);
        related.setRelatedChanel(hostKey);

        Related hostRelated = new Related(key, false, -1, ByteBuffer.allocate(1024), ByteBuffer.allocate(1024), 0, related.getConnectionNum());
        hostKey.attach(hostRelated);
        related.getReadThis().clear();


        ByteBuffer resp = ByteBuffer.allocate(10);
        byte[] first = {5, 0, 0, 1};
        byte[] remoteIp = {array[4], array[5], array[6], array[7], array[8]};
        byte[] remotePort = {array[9]};
        resp.put(first);
        resp.put(remoteIp);
        resp.put(remotePort);
        resp.flip();

        related.setWriteThis(resp);
        related.setCountMes(2);
        if(flag) {
            related.getRelatedChanel().interestOps(SelectionKey.OP_CONNECT);
            key.interestOps(0);
        }
        else {
            key.interestOps(SelectionKey.OP_WRITE);
        }

    }

    void connect(SelectionKey key) {
        Related attachment = (Related) key.attachment();
        System.out.println(attachment.getConnectionNum() + " finish connect");
        try {
            ((SocketChannel)key.channel()).finishConnect();
            System.out.println(attachment.getConnectionNum() + "conn finished succs");
            attachment.getRelatedChanel().interestOps(SelectionKey.OP_WRITE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void thirdMessage(SelectionKey key, Related related) {
        System.out.println(related.getConnectionNum() + " third mes");
        try {

            ((SocketChannel) key.channel()).write(related.getWriteThis());
            key.interestOps(SelectionKey.OP_READ);
            ((Related) key.attachment()).getRelatedChanel().interestOps(SelectionKey.OP_READ);

            related.setCountMes(3);
            related.getWriteThis().clear();

        } catch (ConnectException e1) {
            e1.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

        void firstMessage(SelectionKey key, Related realted) throws IOException {
        byte[] array  = realted.getReadThis().array();
        if(array[0] != 5) {
            System.out.println(realted.getConnectionNum() + "Unsupported socks version");
        }
        realted.setCountMes(1);
        byte[] immediateAnswer = {5, 0};
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer writeThis = ByteBuffer.wrap(immediateAnswer);

        if(channel.write(writeThis) == -1) {
            System.out.println("couldn't erite in first mes (fix it)");
            closeChanel(key);
        }
    }


     void closeChanel(SelectionKey key) throws IOException {

        Related related = (Related)key.attachment();
        System.out.println(related.getConnectionNum() + "Chanel close");

         if(!related.isClient()) {
             related.getRelatedChanel().interestOps(0);
             key.interestOps(0);
             key.channel().close();
             related.getRelatedChanel().channel().close();
             related.getRelatedChanel().cancel();
             key.cancel();
         }
         else {
             if(related.getRelatedChanel() != null) {
                 related.getRelatedChanel().interestOps(0);
                 related.getRelatedChanel().channel().close();
                 related.getRelatedChanel().cancel();
                 related.setRelatedChanel(null);
                 key.interestOps(SelectionKey.OP_WRITE);
             }
         }

    }

    private void finalClose(SelectionKey key) {
        try {
            key.interestOps(0);
            key.channel().close();
            key.cancel();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void write(SelectionKey key) throws IOException {
        Related relaed = (Related)key.attachment();
        System.out.println(relaed.getConnectionNum() + " write");
        SocketChannel channel = (SocketChannel)key.channel();


        try {
            if (channel.write(relaed.getWriteThis()) == -1) {
                System.out.println("Can't write");
                if(relaed.getRelatedChanel() == null)
                    finalClose(key);
                else
                    closeChanel(key);
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            if(relaed.getRelatedChanel() == null)
                finalClose(key);
            else
                closeChanel(key);
            return;
        }

        relaed.setReadedNum(0);
        relaed.getWriteThis().clear();
        if (relaed.getRelatedChanel() != null) {
            relaed.getRelatedChanel().interestOps(SelectionKey.OP_READ);
            key.interestOps(SelectionKey.OP_READ);
        }
        else {
            finalClose(key);
        }
    }


}

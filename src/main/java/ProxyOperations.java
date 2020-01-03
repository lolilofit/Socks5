import org.xbill.DNS.*;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


class ProxyOperations {
    private class ResolvePair {
        SelectionKey key;
        byte[] connectingPort;
        String domainName;

        ResolvePair(SelectionKey key1, byte[] connectingPort1, String domainName1) {
            key = key1;
            connectingPort = connectingPort1;
            domainName = domainName1;
        }
    }

    private int cnt = 0;
    private DatagramChannel dnsResolveChannel = null;
    private int dnsMesId = 1;
    private Map<Integer, ResolvePair> stotedPairs = new HashMap<>();

    DatagramChannel createDatagramChannel() {
        try {
            dnsResolveChannel = DatagramChannel.open();
            dnsResolveChannel.configureBlocking(false);
            DatagramSocket socket = dnsResolveChannel.socket();
            socket.bind(new InetSocketAddress(101));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return dnsResolveChannel;
    }

    private boolean setDnsRequest(byte[] array, SelectionKey key) {
        String dnsServers[] = ResolverConfig.getCurrentConfig().servers();
        if(dnsServers.length == 0)
            return false;

        byte[] name = Arrays.copyOfRange(array, 5, array[4] + 5);
        int portPos = (array[4] + 5);
        byte[] port = {array[portPos], array[portPos+1]};
        ResolvePair resolvePair = new ResolvePair(key, port, new String(name));
        stotedPairs.put(dnsMesId, resolvePair);

        Message message = new Message();
        Header header = message.getHeader();
        header.setID(dnsMesId);
        dnsMesId++;
        header.setOpcode(Opcode.QUERY);
        header.setRcode(Rcode.NOERROR);
        header.setFlag(Flags.RD);
        try {
            Name nameObj = Name.fromString(resolvePair.domainName + ".");
            message.addRecord(Record.newRecord(nameObj, Type.A, DClass.IN), Section.QUESTION);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        byte[] data = message.toWire();

        try {
            ByteBuffer sendThis = ByteBuffer.allocate(data.length);
            sendThis.clear();
            sendThis.put(data);
            sendThis.rewind();

            (dnsResolveChannel).send(sendThis, new InetSocketAddress(dnsServers[0], 53));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    void readFromChanel(SelectionKey key) {
        SocketChannel client = null;
        DatagramChannel udpClient = null;
        if((key.channel()) instanceof  SocketChannel)
            client = (SocketChannel) key.channel();
        else
            udpClient = (DatagramChannel) key.channel();

        Related related = (Related) key.attachment();
        if(related == null) {
            key.attach(new Related(
                    null,
                    true,
                    0,
                     ByteBuffer.allocate(1024),
                     ByteBuffer.allocate(1024),
                    cnt));
            cnt++;
            related = (Related) key.attachment();

        }
        int c = related.getConnectionNum();
        System.out.println(c + " read");
        int readed = 0;

        try {
            related.getReadThis().clear();
            if(client != null)
                readed = client.read(related.getReadThis());
            if(udpClient != null) {
                udpClient.receive(related.getReadThis());
                readed = 2;
            }

            if (readed < 1) {
                closeChanel(key);
                return;
            }
            System.out.println("readed " + readed);
        }
        catch (IOException e) {
            closeChanel(key);
            e.printStackTrace();
            return;
        }
        //if datagram
        if(udpClient != null) {
            connectWithDomainName(related);
            return;
        }
        if(related.getCountMes() == 0 && related.isClient()) {
            firstMessageRead(key, related);
            return;
        }
        if(related.getCountMes() == 1 && related.isClient()) {
            openRemoteConnection(key, related);
            return;
        }
        transfer(key, related);

    }

    private void transfer(SelectionKey key, Related related) {
        System.out.println(related.getConnectionNum() + " transfer ");
        related.getReadThis().flip();

        Related relatedCli = (Related) related.getRelatedChanel().attachment();
        if(relatedCli != null) {
            relatedCli.setWriteThis(related.getReadThis());
        }

        related.getRelatedChanel().interestOps(SelectionKey.OP_WRITE);
        key.interestOps(SelectionKey.OP_WRITE & key.interestOps());
    }


    private void connectWithDomainName(Related related) {
        byte[] array  = related.getReadThis().array();
        try {
            Message message = new Message(array);
            Header header = message.getHeader();
            int id = header.getID();
            Record[] records = message.getSectionArray(Section.ANSWER);
            if(records == null) return;

            ResolvePair pair = stotedPairs.get(id);
            Related clientRelated = ((Related)pair.key.attachment());
            if(clientRelated != null) {
                for (Record record : records) {
                    if (record instanceof ARecord) {
                        ARecord ptr = (ARecord) record;
                        InetAddress adr = ptr.getAddress();
                        byte[] byteAdr = {0, 0, 0, 0};
                        connectWithIpV4(pair.key, clientRelated, byteAdr, pair.connectingPort, adr);
                        return;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectWithIpV4(SelectionKey key, Related related, byte[] addrToCon, byte[] remotePort, InetAddress a) {

        int intPort = (0xFF & remotePort[0]) * 256 + 0xFF & remotePort[1];
        SocketChannel hostConnection = null;
        boolean flag = false;

        try {
            hostConnection = SocketChannel.open();
            hostConnection.configureBlocking(false);

            if (!hostConnection.connect(new InetSocketAddress(a, intPort))) {
                System.out.println(related.getConnectionNum() + " can't connect now");
                flag = true;
            }
            SelectionKey hostKey = hostConnection.register(key.selector(), SelectionKey.OP_READ);
            related.setRelatedChanel(hostKey);

            Related hostRelated = new Related(key, false, -1, ByteBuffer.allocate(1024), ByteBuffer.allocate(1024), 0);
            hostKey.attach(hostRelated);
            related.getReadThis().clear();
        } catch (IOException e) {
            e.printStackTrace();
            //send bad answer
        }

        setSuccessfulAnswer(related, addrToCon, remotePort);

        related.setCountMes(2);
        if (flag) {
            related.getRelatedChanel().interestOps(SelectionKey.OP_CONNECT);
            key.interestOps(0);
        } else {
            key.interestOps(SelectionKey.OP_WRITE);
            //related.getRelatedChanel().interestOps(0);
        }
    }

    private void setSuccessfulAnswer(Related related, byte[] remoteIp, byte[] remotePort) {
        ByteBuffer resp = ByteBuffer.allocate(10);
        byte[] first = {5, 0, 0, 1};
        resp.put(first);
        resp.put(remoteIp);
        resp.put(remotePort);
        resp.flip();
        related.setWriteThis(resp);
    }

    private void setNotSupportedAns(Related related, byte[] gotArray) {
        ByteBuffer resp = ByteBuffer.allocate(gotArray.length);
        gotArray[1] = 7;
        resp.put(gotArray);
        resp.flip();
        related.setWriteThis(resp);
    }

    private void openRemoteConnection(SelectionKey key, Related related) {
        byte[] array  = related.getReadThis().array();
        if(array[1] != 1 || array[9] == 0) {
            if(array[1] != 1)
                System.out.println(related.getConnectionNum() + " DOMAIN NAME!!");
            return;
        }
        if(array[3] == 1) {
            String ipAdr = (0xFF & array[4]) + "." + (0xFF & array[5]) + "." + (0xFF & array[6]) + "." + (0xFF & array[7]);
            byte[] addrToCon = {array[4], array[5], array[6], array[7]};
            byte[] remotePort = {array[8], array[9]};
            int intPort = (0xFF & remotePort[0]) * 256 + 0xFF & remotePort[1];
            System.out.println(related.getConnectionNum() + " create connection with:" + ipAdr + " port: " + intPort);

            try {
                InetAddress a = InetAddress.getByAddress(addrToCon);
                connectWithIpV4(key, related, addrToCon, remotePort, a);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            return;
        }
        if(array[3] == 3) {
            setDnsRequest(array, key);
            key.interestOps(0);
            return;
        }

        setNotSupportedAns(related, array);
        key.interestOps(SelectionKey.OP_WRITE);
        related.getRelatedChanel().interestOps(0);
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

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

        private void firstMessageRead(SelectionKey key, Related related) {
            byte[] array = related.getReadThis().array();
            if (array[0] != 5) {
                System.out.println(related.getConnectionNum() + "Unsupported socks version");
            }
            related.setCountMes(1);
            byte[] immediateAnswer = {5, 0};
            ByteBuffer writeThis = ByteBuffer.wrap(immediateAnswer);
            related.setWriteThis(writeThis);
            key.interestOps(SelectionKey.OP_WRITE);
        }


     private void closeChanel(SelectionKey key) {

        Related related = (Related)key.attachment();
        System.out.println(related.getConnectionNum() + "Chanel close");

         if(!related.isClient()) {
             related.getRelatedChanel().interestOps(0);
             key.interestOps(0);
             try {
                 if(key.channel() instanceof  SocketChannel)
                    ((SocketChannel)key.channel()).socket().close();
                 key.channel().close();
             } catch (IOException e) {
                 e.printStackTrace();
             }
             try {
                 if(related.getRelatedChanel().channel() instanceof  SocketChannel)
                    ((SocketChannel)related.getRelatedChanel().channel()).socket().close();
                 related.getRelatedChanel().channel().close();
             } catch (IOException e) {
                 e.printStackTrace();
             }
         }
         else {
             if(related.getRelatedChanel() != null) {
                 related.getRelatedChanel().interestOps(0);
                 try {
                     related.getRelatedChanel().channel().close();
                 } catch (IOException e) {
                     e.printStackTrace();
                 }
                 related.getRelatedChanel().cancel();
                 related.setRelatedChanel(null);
             }
             else {finalClose(key);}
         }

    }

    private void finalClose(SelectionKey key) {
        try {
            key.interestOps(0);
            if(key.channel() instanceof  SocketChannel)
                ((SocketChannel)key.channel()).socket().close();
            key.channel().close();
            //key.cancel();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void write(SelectionKey key) {
        Related related = (Related)key.attachment();
        System.out.println(related.getConnectionNum() + " write");
        SocketChannel channel = (SocketChannel)key.channel();


        try {
            if (channel.write(related.getWriteThis()) == -1) {
                System.out.println("Can't write");
                if(related.getRelatedChanel() == null)
                    finalClose(key);
                else
                    closeChanel(key);
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            if(related.getRelatedChanel() == null)
                finalClose(key);
            else
                closeChanel(key);
            return;
        }

        related.getWriteThis().clear();
        if (related.getRelatedChanel() != null) {
            related.getRelatedChanel().interestOps(SelectionKey.OP_READ);
        }
        key.interestOps(SelectionKey.OP_READ);
    }


}

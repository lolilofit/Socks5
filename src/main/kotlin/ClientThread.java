import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ClientThread implements Runnable {
    Socket client = null;

    public void run() {
        try {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(client.getOutputStream()));
        DataInputStream in = new DataInputStream(new BufferedInputStream(client.getInputStream()));

        byte[] firstMes = new byte[1024];
        int readen = 0;
        readen = in.read(firstMes);
        if(readen < 1) {
            System.out.println("can't read");
            client.close();
            return;
        }
        if(firstMes[0] != 5) {
            System.out.println("Unsupported socks version");
            //close conn
            client.close();
            return;
        }
        byte[] immediateAnswer = {5, 0};
        out.write(immediateAnswer);
        out.flush();

        byte[] request = new byte[1024];
        readen = in.read(request);
        if(readen < 0) { client.close(); return;}
        if(request[1] != 1 || request[9] == 0) {
            client.close();
            return;
        }
        if(request[3] != 1) {System.out.println("I can't work with thiss type of addr"); return;}
        int[] addr = new int[] { 0xFF & request[4], 0xFF & request[5], 0xFF & request[6], 0xFF & request[7] };
        String ipAdr = addr[0] + "." + addr[1] + "." + addr[2] + "." + addr[3];
        int p = (0xFF & request[9]);
        System.out.println("create connection with:" + ipAdr + " port " + p);

        Socket remoteHost = new Socket();
        remoteHost.connect(new InetSocketAddress(ipAdr, p));

        ByteBuffer resp = ByteBuffer.allocate(10);
        byte[] first = {5, 0, 0, 1};
        byte[] remoteIp = {request[4], request[5], request[6], request[7], request[8]};
        byte[] remotePort = {request[9]};
        resp.put(first);
        resp.put(remoteIp);
        resp.put(remotePort);

        byte[] ans = resp.array();

        out.write(resp.array());
        out.flush();


        byte[] re = new byte[1024];
        readen = in.read(re);
        if(readen < 1) {
                System.out.println("can't read");
                client.close();
                return;
        }

        DataOutputStream remoteOut = new DataOutputStream(new BufferedOutputStream(remoteHost.getOutputStream()));
        DataInputStream remoteIn = new DataInputStream(new BufferedInputStream(remoteHost.getInputStream()));

        byte[] writeThis = new byte[readen];
        System.arraycopy(re, 0, writeThis, 0, readen);
        remoteOut.write(writeThis);
        remoteOut.flush();

        while(true) {
            byte[] mes = new byte[1024];
            readen = remoteIn.read(mes);
            if(readen < 1) {
                System.out.println("can't read");
                client.close();
                remoteHost.close();
                break;
            }
            out.write(mes);
            out.flush();
        }
        } catch (IOException e) {
            e.printStackTrace();
            try {
                client.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return;
        }
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

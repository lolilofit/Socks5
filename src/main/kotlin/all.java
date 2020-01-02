import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class all {
    void run() {
        int port = 100;
        ServerSocket serverSocket = null;

        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        while(true) {

            try {
                Socket client = serverSocket.accept();
                ClientThread cli = new ClientThread();
                cli.client = client;
                Thread thread = new Thread(cli);
                thread.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

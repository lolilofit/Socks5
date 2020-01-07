import java.util.Scanner;

public class Main {
    public static void main(String[] argv) {
        System.out.println("Enter port number");
        Scanner in = new Scanner(System.in);
        int port = in.nextInt();

        Proxy proxy = new Proxy();
        proxy.run(port);
    }
}

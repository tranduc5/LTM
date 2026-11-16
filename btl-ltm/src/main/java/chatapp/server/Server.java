package chatapp.server;

import chatapp.logic.CommandProcessor;
import chatapp.logic.RoomManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static final int DEFAULT_PORT = 5000;

    private static final java.io.PrintStream OUT =
            new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                    java.nio.charset.StandardCharsets.UTF_8);

    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final CommandProcessor processor = new CommandProcessor(new RoomManager(Server::log));

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Server đang lắng nghe tại cổng " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                log("Kết nối mới từ " + socket.getRemoteSocketAddress());
                pool.execute(new ClientHandler(socket, processor));
            }
        }
    }

    public static void log(String msg) {
        OUT.println("[" + java.time.LocalTime.now().withNano(0) + "] " + msg);
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new Server(port).start();
    }
}

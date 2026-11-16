package chatapp.server;

import chatapp.logic.ChatUser;
import chatapp.logic.CommandProcessor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Xử lý socket của một client, chạy trong luồng riêng (chủ sở hữu: TV1).
 * Chỉ đọc/ghi dòng; việc hiểu lệnh do CommandProcessor (TV2) đảm nhiệm.
 */
public class ClientHandler implements Runnable, ChatUser {
    private final Socket socket;
    private final CommandProcessor processor;
    private PrintWriter out;

    private volatile String token;
    private volatile String name;
    private volatile String room;
    private volatile boolean watching;

    public ClientHandler(Socket socket, CommandProcessor processor) {
        this.socket = socket;
        this.processor = processor;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            processor.onConnect(this);
            String line;
            while ((line = in.readLine()) != null) {
                if (!processor.handle(this, line.trim())) break;
            }
        } catch (IOException ignored) {
            // client ngắt đột ngột
        } finally {
            processor.onDisconnect(this);
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    // ---------- ChatUser ----------

    @Override public String getToken() {return token;}
    @Override public void setToken(String token) {this.token=token;}
    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }
    @Override public String getRoom() { return room; }
    @Override public void setRoom(String room) { this.room = room; }
    @Override public boolean isWatching() { return watching; }
    @Override public void setWatching(boolean watching) { this.watching = watching; }
    @Override public String getAddress() { return String.valueOf(socket.getRemoteSocketAddress()); }

    /** Gửi một dòng cho client này (an toàn khi nhiều luồng cùng gửi). */
    @Override
    public synchronized void send(String line) {
        if (out != null) out.println(line);
    }
}

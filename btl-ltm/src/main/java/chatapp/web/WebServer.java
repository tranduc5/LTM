package chatapp.web;

import chatapp.server.Server;
import chatapp.logic.ChatDatabase;
import chatapp.logic.Sessions;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Cổng web (chủ sở hữu: TV1): phục vụ trang web (HTTP) và cầu nối WebSocket -> chat server TCP.
 * Mỗi kết nối WebSocket mở một kết nối TCP riêng tới chat server, nên trình duyệt
 * hoạt động như một client bình thường (chat chung được với GuiClient/CliClient).
 *
 * Chạy: java WebServer [webPort] [chatPort]   (mặc định 8080 và 5000)
 * Server chat TCP được khởi động cùng trong tiến trình này.
 */
public class WebServer {
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public static void main(String[] args) throws Exception {
        int webPort = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int chatPort = args.length > 1 ? Integer.parseInt(args[1]) : Server.DEFAULT_PORT;

        // Khởi tạo SQLite/logic trước khi mở HTTP: tránh hiện trang đăng nhập
        // trong khi TCP Server đã chết do không mở được database.
        Server backend = new Server(chatPort);
        Thread chat = new Thread(() -> {
            try {
                backend.start();
            } catch (Exception e) {
                Server.log("Không khởi động được TCP chat server cổng " + chatPort + ": " + e);
                e.printStackTrace();
                System.exit(1);
            }
        }, "chat-server");
        chat.setDaemon(true);
        chat.start();
        Thread.sleep(300);

        try (ServerSocket ss = new ServerSocket(webPort)) {
            Server.log("Mở trình duyệt tại http://localhost:" + webPort);
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> handle(s, chatPort)).start();
            }
        }
    }

    // ---------- HTTP ----------

    private static void handle(Socket s, int chatPort) {
        try {
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] req = lines[0].split(" ");
            Map<String, String> h = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                if (c > 0) h.put(lines[i].substring(0, c).trim().toLowerCase(), lines[i].substring(c + 1).trim());
            }
            String key = h.get("sec-websocket-key");
            if ("websocket".equalsIgnoreCase(h.get("upgrade")) && key != null) {
                websocket(s, in, out, key, chatPort);
            } else {
                String path=req.length>1?req[1]:"/";
                if(path.matches("/files/[a-f0-9-]{36}")) serveFile(out,path,h.get("authorization"));
                else serveStatic(out,path);
            }
        } catch (IOException ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    private static void serveFile(OutputStream out,String path,String authorization) throws IOException {
        String token=authorization!=null&&authorization.startsWith("Bearer ")?authorization.substring(7):"";
        String user=Sessions.user(token);
        if(user==null){httpError(out,"401 Unauthorized");return;}
        String[] f=new ChatDatabase().attachment(path.substring(7),user);
        if(f==null){httpError(out,"403 Forbidden");return;}
        Path file=Path.of("uploads",f[0]);
        if(!Files.isRegularFile(file)){httpError(out,"404 Not Found");return;}
        byte[] body=Files.readAllBytes(file);
        String type=f[1];
        String name=java.net.URLEncoder.encode(f[2],StandardCharsets.UTF_8).replace("+","%20");
        String disposition=type.startsWith("image/")?"inline":"attachment";
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+type+"\r\nX-Content-Type-Options: nosniff\r\nCache-Control: no-store\r\nContent-Disposition: "+disposition+"; filename*=UTF-8''"+name+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);out.flush();
    }
    private static void httpError(OutputStream out,String status)throws IOException{
        byte[] b=status.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 "+status+"\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "+b.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(b);out.flush();
    }

    private static String readHead(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            sb.append((char) c);
            if (sb.length() >= 4 && sb.substring(sb.length() - 4).equals("\r\n\r\n")) return sb.toString();
            if (sb.length() > 16384) return null;
        }
        return null;
    }

    private static void serveStatic(OutputStream out, String path) throws IOException {
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        if (path.equals("/")) path = "/index.html";
        byte[] body = null;
        String type = "text/plain; charset=utf-8";
        // Chỉ phục vụ file .html/.css/.js nằm trực tiếp trong resources/web (không cho phép dấu '/' thứ hai nên không thoát thư mục được)
        if (path.matches("/[A-Za-z0-9_.-]+\\.(html|css|js)")) {
            try (InputStream res = WebServer.class.getResourceAsStream("/web" + path)) {
                if (res != null) {
                    body = res.readAllBytes();
                    type = path.endsWith(".css") ? "text/css; charset=utf-8"
                         : path.endsWith(".js") ? "text/javascript; charset=utf-8"
                         : "text/html; charset=utf-8";
                }
            }
        }
        String status = "200 OK";
        if (body == null) {
            body = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            status = "404 Not Found";
            type = "text/plain; charset=utf-8";
        }
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    // ---------- WebSocket (RFC 6455, tối giản) ----------

    private static void websocket(Socket ws, InputStream in, OutputStream out, String key, int chatPort)
            throws IOException {
        String accept;
        try {
            accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + WS_GUID).getBytes(StandardCharsets.ISO_8859_1)));
        } catch (Exception e) {
            throw new IOException(e);
        }
        // Chỉ nâng cấp HTTP lên WebSocket sau khi kết nối TCP thành công.
        // Nếu TCP Server chưa sẵn sàng, phản hồi 503 để dễ chẩn đoán.
        Socket chat;
        try {
            chat = new Socket("127.0.0.1", chatPort);
        } catch (IOException e) {
            Server.log("WebSocket không kết nối được tới TCP chat port " + chatPort + ": " + e.getMessage());
            httpError(out, "503 Service Unavailable");
            return;
        }
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        PrintWriter toChat = new PrintWriter(new OutputStreamWriter(chat.getOutputStream(), StandardCharsets.UTF_8), true);

        // chat server -> trình duyệt
        Thread pump = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(chat.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sendFrame(out, 0x1, line.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            } finally {
                try { sendFrame(out, 0x8, new byte[0]); } catch (IOException ignored) { }
                try { ws.close(); } catch (IOException ignored) { }
            }
        }, "ws-pump");
        pump.setDaemon(true);
        pump.start();

        // trình duyệt -> chat server
        try {
            while (true) {
                int b0 = in.read();
                if (b0 < 0) break;
                int opcode = b0 & 0x0F;
                int b1 = in.read();
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                if (len == 126) len = (in.read() << 8) | in.read();
                else if (len == 127) { len = 0; for (int i = 0; i < 8; i++) len = (len << 8) | in.read(); }
                if (len > 1_000_000) break;
                byte[] mask = new byte[4];
                if (masked) readFully(in, mask);
                byte[] payload = new byte[(int) len];
                readFully(in, payload);
                if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];

                if (opcode == 0x8) break;                                 // close
                if (opcode == 0x9) sendFrame(out, 0xA, payload);          // ping -> pong
                else if (opcode == 0x1 || opcode == 0x0) {                // text
                    String text = new String(payload, StandardCharsets.UTF_8).replace("\r", " ").replace("\n", " ");
                    toChat.println(text);
                }
            }
        } catch (IOException ignored) {
        } finally {
            try { chat.close(); } catch (IOException ignored) { }  // server sẽ thấy EOF và dọn dẹp
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new EOFException();
            off += n;
        }
    }

    private static void sendFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        synchronized (out) {
            out.write(0x80 | opcode);
            if (payload.length < 126) {
                out.write(payload.length);
            } else if (payload.length < 65536) {
                out.write(126);
                out.write(payload.length >> 8);
                out.write(payload.length & 0xFF);
            } else {
                out.write(127);
                for (int i = 7; i >= 0; i--) out.write((int) ((long) payload.length >> (8 * i)) & 0xFF);
            }
            out.write(payload);
            out.flush();
        }
    }
}

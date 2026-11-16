package chatapp.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Quản lý người dùng, phòng và phát tin (chủ sở hữu: TV2).
 *
 * <p>Toàn bộ trạng thái dùng chung được bảo vệ bằng {@code synchronized} trên đối tượng này.
 * Mọi thay đổi người dùng/phòng PHẢI đi qua lớp này. Các phương thức để package-private
 * vì chỉ {@link CommandProcessor} gọi; tầng mạng (TV1) chỉ cần dùng constructor.
 */
public class RoomManager {
    public static final String LOBBY = "lobby";

    private final Map<String, ChatUser> users = new HashMap<>();             // nick (chữ thường) -> user
    private final Map<String, Set<ChatUser>> rooms = new TreeMap<>();        // tên phòng (chữ thường) -> thành viên
    private final Consumer<String> logger;
    private final ChatDatabase db;

    private static final java.time.format.DateTimeFormatter TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    /** @param logger nơi ghi log (tầng mạng truyền vào, ví dụ Server::log) */
    public RoomManager(Consumer<String> logger) {
        this.logger = logger;
        this.db = new ChatDatabase();
        for (String r : db.rooms()) rooms.put(r.toLowerCase(), new LinkedHashSet<>());
        rooms.putIfAbsent(LOBBY, new LinkedHashSet<>());
    }

    // ---------- Người dùng ----------

    synchronized boolean register(ChatUser u, String nick) {
        String key = nick.toLowerCase();
        if (users.containsKey(key)) return false;
        users.put(key, u);
        u.setName(nick);
        logger.accept(nick + " đăng nhập từ " + u.getAddress());
        return true;
    }

    /** Gọi khi client ngắt kết nối (bình thường hoặc đột ngột). */
    synchronized void disconnect(ChatUser u) {
        if (u.getName() == null) return;
        leaveCurrentRoom(u);
        users.remove(u.getName().toLowerCase());
        Sessions.revoke(u.getToken());
        u.setToken(null);
        logger.accept(u.getName() + " đã thoát");
        u.setName(null);
        broadcastLists();
    }

    // ---------- Phòng ----------

    synchronized void join(ChatUser u, String room) {
        room = room.toLowerCase();
        if (room.equals(u.getRoom())) {
            u.send("INFO\tBạn đang ở phòng '" + room + "' rồi");
            return;
        }
        leaveCurrentRoom(u);
        boolean created = !rooms.containsKey(room);
        if (created) db.addRoom(room);
        rooms.computeIfAbsent(room, k -> new LinkedHashSet<>()).add(u);
        u.setRoom(room);
        u.send("JOINED\t" + room);
        for (String[] h : db.roomHistory(room)) {
            if (h[0].equals("TEXT")) u.send("HISTORY\t"+room+"\t"+h[1]+"\t"+h[2]+"\t"+h[4]);
            else u.send("FILEMSG\tROOM\t"+room+"\t"+h[2]+"\t"+h[5]+"\t"+h[4]+"\t"+h[1]);
        }
        if (created) u.send("INFO\tĐã tạo phòng mới '" + room + "'");
        broadcastRoom(room, "SYSTEM\t" + room + "\t" + u.getName() + " đã vào phòng", u);
        logger.accept(u.getName() + " vào phòng " + room);
        broadcastLists();
    }

    /** Rời phòng hiện tại (không gửi danh sách). Phòng đã tạo được giữ lại kể cả khi không còn ai. */
    private void leaveCurrentRoom(ChatUser u) {
        String old = u.getRoom();
        if (old == null) return;
        Set<ChatUser> members = rooms.get(old);
        if (members != null) {
            members.remove(u);
            broadcastRoom(old, "SYSTEM\t" + old + "\t" + u.getName() + " đã rời phòng", null);
        }
        u.setRoom(null);
    }

    // ---------- Tin nhắn ----------

    synchronized void chat(ChatUser u, String text) {
        if (u.getRoom() == null) {
            u.send("ERROR\tBạn chưa ở trong phòng nào");
            return;
        }
        db.addMessage(u.getName(),u.getRoom(),null,text,"TEXT");
        broadcastRoom(u.getRoom(), "CHAT\t" + u.getRoom() + "\t" + u.getName() + "\t" + text, null);
    }

    synchronized void privateMessage(ChatUser from, String toNick, String text) {
        ChatUser to = users.get(toNick.toLowerCase());
        if (!db.exists(toNick)) {
            from.send("ERROR\tTài khoản không tồn tại: " + toNick);
            return;
        }
        db.addMessage(from.getName(),null,toNick,text,"TEXT");
        String line = "PM\t" + from.getName() + "\t" + (to != null ? to.getName() : toNick) + "\t" + text;
        if (to != null) to.send(line);
        if (to != from) from.send(line);
    }

    synchronized void privateHistory(ChatUser u,String other) {
        if (!db.exists(other)) {u.send("ERROR\tTài khoản không tồn tại");return;}
        u.send("PMCLEAR\t"+other);
        for(String[] h:db.pmHistory(u.getName(),other)) {
            if(h[0].equals("TEXT")) u.send("PMHISTORY\t"+h[2]+"\t"+h[3]+"\t"+h[4]+"\t"+h[1]);
            else u.send("FILEMSG\tPM\t"+h[3]+"\t"+h[2]+"\t"+h[5]+"\t"+h[4]+"\t"+h[1]);
        }
    }

    synchronized void file(ChatUser u,String mode,String target,String original,String mime,byte[] contents) {
        if(contents.length<1 || contents.length>600000){u.send("ERROR\tFile phải có dung lượng từ 1 byte đến 600 KB");return;}
        if(!original.matches("[\\p{L}\\p{N}_.() -]{1,100}") || original.contains("..")) {u.send("ERROR\tTên file không hợp lệ");return;}
        String lower=original.toLowerCase(java.util.Locale.ROOT);
        boolean image=lower.endsWith(".png")||lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".gif")||lower.endsWith(".webp");
        if(!(image||lower.endsWith(".pdf")||lower.endsWith(".txt")||lower.endsWith(".zip"))){u.send("ERROR\tĐịnh dạng file không được hỗ trợ");return;}
        mime=lower.endsWith(".png")?"image/png":lower.endsWith(".jpg")||lower.endsWith(".jpeg")?"image/jpeg":lower.endsWith(".gif")?"image/gif":lower.endsWith(".webp")?"image/webp":lower.endsWith(".pdf")?"application/pdf":lower.endsWith(".txt")?"text/plain":"application/zip";
        String room= mode.equals("ROOM")?u.getRoom():null;
        String receiver= mode.equals("PM")?target:null;
        if(mode.equals("ROOM") && (room==null || !room.equalsIgnoreCase(target))){u.send("ERROR\tChỉ gửi file trong phòng hiện tại");return;}
        if(mode.equals("PM") && !db.exists(target)){u.send("ERROR\tTài khoản không tồn tại");return;}
        if(!mode.equals("ROOM") && !mode.equals("PM")){u.send("ERROR\tLoại cuộc trò chuyện không hợp lệ");return;}
        String id=java.util.UUID.randomUUID().toString();
        try {
            java.nio.file.Path dir=java.nio.file.Path.of("uploads");java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve(id),contents,java.nio.file.StandardOpenOption.CREATE_NEW);
            long mid=db.addMessage(u.getName(),room,receiver,original,image?"IMAGE":"FILE");
            db.addAttachment(id,mid,original,mime,id,contents.length);
        }catch(Exception e){u.send("ERROR\tKhông lưu được file");return;}
        String time=java.time.LocalTime.now().format(TIME);
        String line="FILEMSG\t"+mode+"\t"+target+"\t"+u.getName()+"\t"+id+"\t"+original+"\t"+time;
        if(mode.equals("ROOM"))broadcastRoom(room,line,null);
        else {ChatUser to=users.get(target.toLowerCase());if(to!=null)to.send(line);if(to!=u)u.send(line);}
    }

    synchronized void people(ChatUser u){
        StringJoiner sj=new StringJoiner(",");
        for(String person:db.users())sj.add(person+":"+(users.containsKey(person.toLowerCase())?"online":"offline"));
        u.send("PEOPLE\t"+sj);
    }

    private void broadcastRoom(String room, String line, ChatUser exclude) {
        Set<ChatUser> members = rooms.get(room);
        if (members == null) return;
        for (ChatUser m : new ArrayList<>(members)) {
            if (m != exclude) m.send(line);
        }
    }

    // ---------- Danh sách phòng / người dùng ----------

    synchronized String roomsLine() {
        StringJoiner sj = new StringJoiner(",");
        for (Map.Entry<String, Set<ChatUser>> e : rooms.entrySet()) {
            sj.add(e.getKey() + ":" + e.getValue().size());
        }
        return "ROOMS\t" + sj;
    }

    synchronized String usersLine(String room) {
        StringJoiner sj = new StringJoiner(",");
        Set<ChatUser> members = room == null ? null : rooms.get(room);
        if (members != null) for (ChatUser m : members) sj.add(m.getName());
        return "USERS\t" + (room == null ? "" : room) + "\t" + sj;
    }

    /** Gửi danh sách phòng + người dùng cho client đã bật WATCH (GUI/web dùng). */
    synchronized void pushTo(ChatUser u) {
        if (!u.isWatching()) return;
        u.send(roomsLine());
        u.send(usersLine(u.getRoom()));
        people(u);
    }

    private void broadcastLists() {
        for (ChatUser u : new ArrayList<>(users.values())) pushTo(u);
    }
}

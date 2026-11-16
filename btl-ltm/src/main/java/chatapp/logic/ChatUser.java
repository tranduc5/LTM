package chatapp.logic;

/**
 * HỢP ĐỒNG giữa tầng mạng (TV1) và tầng logic (TV2).
 *
 * <p>Chủ sở hữu: TV2. ClientHandler (TV1) implements interface này; RoomManager và
 * CommandProcessor (TV2) chỉ làm việc với interface, không biết gì về socket.
 * Muốn thêm/sửa phương thức ở đây phải báo cả nhóm.
 */
public interface ChatUser {
    /** Nickname, null nếu chưa đăng nhập. */
    String getToken();
    void setToken(String token);
    String getName();
    void setName(String name);

    /** Tên phòng hiện tại, null nếu chưa vào phòng. */
    String getRoom();
    void setRoom(String room);

    /** true nếu client đã bật WATCH (nhận danh sách phòng/người dùng tự động). */
    boolean isWatching();
    void setWatching(boolean watching);

    /** Địa chỉ để ghi log, ví dụ "/127.0.0.1:50000". */
    String getAddress();

    /** Gửi một dòng cho client này. Phải an toàn khi nhiều luồng cùng gọi. */
    void send(String line);
}

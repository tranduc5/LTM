package chatapp.client;

/** Chuyển lệnh người dùng gõ (/join, /pm, ...) thành lệnh gửi lên server. */
public final class Protocol {
    private Protocol() { }

    /** Cổng mặc định của chat server TCP. */
    public static final int DEFAULT_PORT = 5000;

    public static final String HELP =
            "Lệnh:\n"
          + "  /join <phòng>      vào phòng (tự tạo nếu chưa có)\n"
          + "  /leave             quay về phòng lobby\n"
          + "  /rooms             danh sách phòng\n"
          + "  /users             người dùng trong phòng hiện tại\n"
          + "  /pm <tên> <nd>     nhắn riêng\n"
          + "  /people            danh sách online/offline\n"
          + "  /pmhist <tên>      lịch sử chat riêng\n"
          + "  /help              xem trợ giúp\n"
          + "  /quit              thoát\n"
          + "  (gõ chữ thường để chat trong phòng hiện tại)";

    /** @return dòng gửi lên server, hoặc null nếu lệnh sai (nên in HELP). */
    public static String toWire(String input) {
        if (!input.startsWith("/")) return "MSG " + input;
        String[] p = input.substring(1).split("\\s+", 3);
        switch (p[0].toLowerCase()) {
            case "join":  return p.length >= 2 ? "JOIN " + p[1] : null;
            case "leave": return "LEAVE";
            case "rooms": return "ROOMS";
            case "users": return "USERS";
            case "pm":    return p.length >= 3 ? "PM " + p[1] + " " + p[2] : null;
            case "people":return "PEOPLE";
            case "pmhist":return p.length >= 2 ? "PMHIST " + p[1] : null;
            case "quit":  return "QUIT";
            default:      return null;
        }
    }
}

# Multi-room Chat System — Auth, Rooms, PM, Files, Presence, Persistence

Java 11 Maven + TCP Socket + WebSocket bridge + SQLite JDBC. Nâng cấp từ dự án chat nhiều phòng ban đầu; giữ nguyên package `chatapp.client`, `chatapp.logic`, `chatapp.server`, `chatapp.web`.

## Chạy trong NetBeans

1. Máy cần JDK 11+ và Internet **lần đầu** để Maven tải dependency `org.xerial:sqlite-jdbc:3.46.1.3` (được khai báo trong `pom.xml`).
2. Open Project → chọn thư mục này (nơi chứa `pom.xml`).
3. **Clean and Build** để tải driver. **Run Project (F6)** để chạy `chatapp.web.WebServer`.
4. Mở `http://localhost:8080`.
5. Bấm **Đăng ký** (mật khẩu >= 8 ký tự, không có dấu cách); sau khi đăng ký, bấm **Đăng nhập**.
6. Mở tab ẩn danh / trình duyệt khác để đăng nhập tài khoản thứ hai, thử phòng, PM, gửi file, online/offline.
7. Lưu trữ mặc định: `data/chat.db`; file đính kèm: `uploads/`, cả hai được tạo khi chạy và loại trừ bởi `.gitignore`.

## Các chức năng

- **Authentication**: đăng ký, đăng nhập; mật khẩu PBKDF2-HMAC-SHA256 với muối ngẫu nhiên; phiên lưu trong RAM, kết thúc khi ngắt kết nối. Một tài khoản không thể online qua hai phiên đồng thời.
- **Public rooms**: tạo/tự tham gia phòng, broadcast, danh sách phòng và số người; phòng và lịch sử tin nhắn lưu SQLite.
- **Private messages**: nhấp vào tên trong phần "Tin nhắn riêng" để mở hội thoại, lịch sử PM tối đa 100 tin gần nhất; gửi tin khi người nhận offline, họ xem lại khi đăng nhập.
- **Attachments**: ảnh PNG/JPG/GIF/WebP và PDF/TXT/ZIP, tối đa **600 KB/file**, tải xuống chỉ khi xác thực và có quyền với PM tương ứng. Ảnh hiển thị trực tiếp trong Web.
- **Presence**: hiển thị trạng thái Online/Offline theo kết nối, tự cập nhật khi có đăng nhập/đăng xuất/ngắt kết nối.
- **Persistence**: SQLite với bảng users, rooms, messages, attachments.
- **Java CLI/Swing**: đăng nhập tài khoản trước khi chat; chức năng gửi file được tập trung tại Web (CLI/Swing có thể đọc thông báo file).

## Giao thức

Vẫn dùng dạng dòng phân cách tab để tương thích bridge cũ. Client gửi `REGISTER user pass`, `LOGIN user pass`, `JOIN room`, `MSG text`, `PM user text`, `PMHIST user`, `ROOMS`, `USERS`, `PEOPLE`, `WATCH`, `QUIT`.

Upload qua WebSocket với `FILE ROOM room base64(filename) base64(mimetype) base64(bytes)` hoặc `FILE PM user ...`. Download qua `GET /files/<UUID>` với header `Authorization: Bearer <token>` do server cấp khi login. Không đưa token vào URL.

## Kiểm thử thủ công đề xuất

1. Đăng ký `auser` và `buser`, đăng nhập từng tài khoản tại hai cửa sổ khác nhau.
2. Kiểm tra người dùng hiện online; tạo `phong1`, gửi vài tin nhắn.
3. Click tên `buser` trên sidebar, gửi tin riêng, kiểm tra phòng công khai không hiển thị PM.
4. Gửi một ảnh PNG và một PDF nhỏ; tải lại tệp, đối chiếu nội dung.
5. Tắt trình duyệt thứ hai, kiểm tra presence chuyển offline; đăng nhập lại và mở PM để xem lịch sử.
6. Tắt Server, khởi động lại; xác nhận phòng và tin nhắn vẫn còn.
7. Gửi file hơn 600 KB, kiểm tra thông báo từ chối; dùng tài khoản khác thử tải file PM không thuộc mình, xác nhận HTTP 403.

## Hạn chế và lưu ý

- Cấu hình hiện là **demo localhost**, không dùng trực tiếp trên Internet vì TCP và HTTP/WebSocket không mã hóa; cần HTTPS/WSS và TLS cho TCP nếu triển khai thật.
- Mỗi tài khoản chỉ có một phiên kết nối; nếu nhiều thiết bị cần sửa phần quản lý session/RoomManager.
- Giới hạn file 600 KB để tránh làm quá tải WebSocket bridge tối giản; không hỗ trợ gửi video/file lớn hoặc chia khối.
- Room công khai cho phép các tài khoản xác thực tải file của phòng; không phải phòng riêng tư.
- Không triển khai reset mật khẩu/email verification/virus scanning hay rate limiting nâng cao.


## Sửa lỗi đăng nhập / WebSocket

- Trang web mở được nhưng đăng nhập báo không kết nối: kiểm tra cửa sổ Run của NetBeans có `Server đang lắng nghe tại cổng 5000` và `Mở trình duyệt tại http://localhost:8080`.
- Kiểm tra `netstat -ano | findstr :5000` và `netstat -ano | findstr :8080` để phát hiện ứng dụng cũ đang giữ cổng.
- Đăng ký thành công: chuyển sang tab Đăng nhập; bản sửa không còn báo nhầm "Không thể kết nối Server" sau khi đóng WebSocket đăng ký.
- Nếu vẫn không kết nối, nhấn F12 > Network > WS, chọn `/ws` và xem HTTP status / Messages; đồng thời gửi log Run của NetBeans.

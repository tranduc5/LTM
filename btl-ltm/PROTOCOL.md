# Multi-room Chat Protocol v2

Transport: one UTF-8 line per command over TCP. Web Client connects to `WebServer` using WebSocket; each WebSocket has one TCP bridge to the same Server.

## Before authentication
- `REGISTER <username> <password>` -> `REGISTEROK<TAB>message` or `ERROR<TAB>message`
- `LOGIN <username> <password>` -> `LOGINOK<TAB>username<TAB>sessionToken`, followed by JOINED lobby.
- `QUIT` closes the connection.
- `NICK` is no longer supported. Login requires a registered account.

## After authentication
- `JOIN <room>` -> `JOINED<TAB>room`; room history is streamed as HISTORY/FILEMSG.
- `LEAVE` -> back to lobby.
- `MSG <text>` -> `CHAT<TAB>room<TAB>sender<TAB>text` to all users in the room.
- `PM <username> <text>` -> `PM<TAB>sender<TAB>receiver<TAB>text` for sender and recipient (recipient may be offline; SQLite stores history).
- `PMHIST <username>` -> `PMCLEAR<TAB>username` then `PMHISTORY<TAB>sender<TAB>receiver<TAB>text<TAB>HH:mm` and FILEMSG.
- `ROOMS` -> `ROOMS<TAB>room:count,...`
- `USERS` -> `USERS<TAB>room<TAB>username,...`
- `PEOPLE` -> `PEOPLE<TAB>username:online|offline,...`
- `WATCH` pushes updated ROOMS, USERS, PEOPLE when users join/leave.
- `FILE <ROOM|PM> <room|username> <base64(filename)> <base64(mime)> <base64(content)>`
  -> `FILEMSG<TAB>ROOM|PM<TAB>target<TAB>sender<TAB>uuid<TAB>filename<TAB>HH:mm`.
- `GET /files/<uuid>` HTTP with `Authorization: Bearer <sessionToken>` downloads only authorized files.

Username 2–20 Unicode letters/digits/underscore/hyphen; room 1–20; passwords 8–128 characters (no spaces in Web form). Message length <=4000 chars, file size <=600000 bytes; allowed file extensions PNG/JPG/JPEG/GIF/WebP/PDF/TXT/ZIP. Files in room are readable by any authenticated user (public rooms).

Security caveat: demo transport is plain HTTP/TCP for localhost; TLS required before public deployment.

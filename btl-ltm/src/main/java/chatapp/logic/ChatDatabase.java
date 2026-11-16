package chatapp.logic;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** SQLite persistence for accounts, rooms, chat messages and attachments. */
public final class ChatDatabase {
    private final String url;
    public ChatDatabase() {
        try { Files.createDirectories(Path.of("data")); } catch (Exception e) { throw new IllegalStateException(e); }
        url = "jdbc:sqlite:" + Path.of("data", "chat.db").toAbsolutePath();
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY COLLATE NOCASE, salt TEXT NOT NULL, hash TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS rooms (name TEXT PRIMARY KEY COLLATE NOCASE, created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, room TEXT, receiver TEXT, body TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'TEXT', created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS attachments (id TEXT PRIMARY KEY, message_id INTEGER NOT NULL, original_name TEXT NOT NULL, media_type TEXT NOT NULL, stored_name TEXT NOT NULL, size INTEGER NOT NULL, FOREIGN KEY(message_id) REFERENCES messages(id))");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_room_history ON messages(room,id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pm_history ON messages(sender,receiver,id)");
            s.executeUpdate("INSERT OR IGNORE INTO rooms(name) VALUES('lobby')");
        } catch (SQLException e) { throw new IllegalStateException("Không mở được SQLite (kiểm tra sqlite-jdbc dependency)", e); }
    }
    private Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) { s.execute("PRAGMA busy_timeout=5000"); s.execute("PRAGMA foreign_keys=ON"); }
        return c;
    }
    public synchronized boolean signup(String user, String password) {
        if (!user.matches("[\\p{L}\\p{N}_-]{2,20}") || password.length()<8 || password.length()>128) return false;
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        try (Connection c = open(); PreparedStatement p = c.prepareStatement("INSERT INTO users(username,salt,hash) VALUES(?,?,?)")) {
            p.setString(1,user); p.setString(2,Base64.getEncoder().encodeToString(salt)); p.setString(3,derive(password,salt)); p.executeUpdate(); return true;
        } catch (SQLException e) { return false; }
    }
    public synchronized boolean login(String user,String password) {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("SELECT salt,hash FROM users WHERE username=?")) {
            p.setString(1,user);
            try(ResultSet r=p.executeQuery()) { if (!r.next()) return false;
                byte[] salt=Base64.getDecoder().decode(r.getString(1));
                return MessageDigest.isEqual(derive(password,salt).getBytes(java.nio.charset.StandardCharsets.UTF_8),r.getString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch(Exception e) { return false; }
    }
    private static String derive(String password,byte[] salt) {
        char[] chars=password.toCharArray(); PBEKeySpec spec=new PBEKeySpec(chars,salt,210000,256);
        try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
        catch(Exception e){throw new IllegalStateException(e);} finally { spec.clearPassword(); Arrays.fill(chars,'\0'); }
    }
    public synchronized boolean exists(String name) {
        try(Connection c=open(); PreparedStatement p=c.prepareStatement("SELECT 1 FROM users WHERE username=?")) {
            p.setString(1,name);try(ResultSet r=p.executeQuery()){return r.next();}
        }catch(SQLException e){throw new IllegalStateException(e);}
    }
    public synchronized List<String> users() {
        List<String> values=new ArrayList<>();
        try(Connection c=open(); Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT username FROM users ORDER BY username")) {
            while(r.next()) values.add(r.getString(1));
        }catch(SQLException e){throw new IllegalStateException(e);}return values;
    }
    public synchronized List<String> rooms() {
        List<String> values=new ArrayList<>();
        try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT name FROM rooms ORDER BY name")) {
            while(r.next())values.add(r.getString(1));
        }catch(SQLException e){throw new IllegalStateException(e);}return values;
    }
    public synchronized void addRoom(String room){
        try(Connection c=open();PreparedStatement p=c.prepareStatement("INSERT OR IGNORE INTO rooms(name) VALUES(?)")) {p.setString(1,room);p.executeUpdate();}
        catch(SQLException e){throw new IllegalStateException(e);}
    }
    public synchronized long addMessage(String sender,String room,String receiver,String body,String kind) {
        try(Connection c=open();PreparedStatement p=c.prepareStatement("INSERT INTO messages(sender,room,receiver,body,kind,created_at) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1,sender);p.setString(2,room);p.setString(3,receiver);p.setString(4,body);p.setString(5,kind);p.setString(6,java.time.LocalDateTime.now().toString());p.executeUpdate();
            try(ResultSet r=p.getGeneratedKeys()){return r.next()?r.getLong(1):-1;}
        }catch(SQLException e){throw new IllegalStateException(e);}
    }
    // Each record: kind, time, sender, recipient-or-room, body, attachment id (if present)
    public synchronized List<String[]> roomHistory(String room) {
        List<String[]> result=new ArrayList<>();
        String sql="SELECT m.kind,substr(m.created_at,12,5),m.sender,m.room,m.body,COALESCE(a.id,'') FROM messages m LEFT JOIN attachments a ON a.message_id=m.id WHERE m.room=? ORDER BY m.id DESC LIMIT 100";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,room);try(ResultSet r=p.executeQuery()){while(r.next())result.add(record(r));}
        }catch(SQLException e){throw new IllegalStateException(e);} Collections.reverse(result);return result;
    }
    public synchronized List<String[]> pmHistory(String one,String two) {
        List<String[]> result=new ArrayList<>();
        String sql="SELECT m.kind,substr(m.created_at,12,5),m.sender,m.receiver,m.body,COALESCE(a.id,'') FROM messages m LEFT JOIN attachments a ON a.message_id=m.id WHERE (m.sender=? COLLATE NOCASE AND m.receiver=? COLLATE NOCASE) OR (m.sender=? COLLATE NOCASE AND m.receiver=? COLLATE NOCASE) ORDER BY m.id DESC LIMIT 100";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,one);p.setString(2,two);p.setString(3,two);p.setString(4,one);
            try(ResultSet r=p.executeQuery()){while(r.next())result.add(record(r));}
        }catch(SQLException e){throw new IllegalStateException(e);} Collections.reverse(result);return result;
    }
    private static String[] record(ResultSet r)throws SQLException {return new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)};}
    public synchronized void addAttachment(String id,long messageId,String name,String mime,String storedName,long size){
        try(Connection c=open();PreparedStatement p=c.prepareStatement("INSERT INTO attachments(id,message_id,original_name,media_type,stored_name,size) VALUES(?,?,?,?,?,?)")) {
            p.setString(1,id);p.setLong(2,messageId);p.setString(3,name);p.setString(4,mime);p.setString(5,storedName);p.setLong(6,size);p.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException(e);}
    }
    public synchronized String[] attachment(String id,String requester){
        String sql="SELECT a.stored_name,a.media_type,a.original_name FROM attachments a JOIN messages m ON m.id=a.message_id WHERE a.id=? AND (m.room IS NOT NULL OR m.sender=? COLLATE NOCASE OR m.receiver=? COLLATE NOCASE)";
        try(Connection c=open();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,id);p.setString(2,requester);p.setString(3,requester);
            try(ResultSet r=p.executeQuery()){if(r.next())return new String[]{r.getString(1),r.getString(2),r.getString(3)};}
        }catch(SQLException e){throw new IllegalStateException(e);}return null;
    }
}

package chatapp.logic;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
/** Authenticated line protocol, shared by TCP, CLI, GUI and web bridge. */
public class CommandProcessor {
    private final RoomManager rooms;
    private final ChatDatabase db;
    public CommandProcessor(RoomManager rooms) {this.rooms=rooms;this.db=new ChatDatabase();}
    public void onConnect(ChatUser u) {u.send("INFO\tChào mừng! REGISTER <user> <pass> hoặc LOGIN <user> <pass>");}
    public void onDisconnect(ChatUser u) {rooms.disconnect(u);}
    public boolean handle(ChatUser u,String line) {
        if(line==null||line.isEmpty())return true;
        String[] p=line.split("\\s+",2);
        String cmd=p[0].toUpperCase(java.util.Locale.ROOT),arg=p.length>1?p[1].trim():"";
        if(cmd.equals("QUIT")){u.send("INFO\tTạm biệt!");return false;}
        if(cmd.equals("REGISTER")){
            if(u.getName()!=null){u.send("ERROR\tĐã đăng nhập");return true;}
            String[] a=arg.split(" ",2);
            if(a.length!=2||!db.signup(a[0],a[1]))u.send("ERROR\tĐăng ký thất bại: tài khoản trùng, sai định dạng hoặc mật khẩu dưới 8 ký tự");
            else u.send("REGISTEROK\tĐăng ký thành công. Hãy đăng nhập.");
            return true;
        }
        if(cmd.equals("LOGIN")){
            if(u.getName()!=null){u.send("ERROR\tĐã đăng nhập");return true;}
            String[] a=arg.split(" ",2);
            if(a.length!=2||!db.login(a[0],a[1])){u.send("ERROR\tSai tài khoản hoặc mật khẩu");return true;}
            if(!rooms.register(u,a[0])){u.send("ERROR\tTài khoản đang đăng nhập ở nơi khác");return true;}
            String token=Sessions.create(u.getName());u.setToken(token);
            u.send("LOGINOK\t"+u.getName()+"\t"+token);
            rooms.join(u,RoomManager.LOBBY);return true;
        }
        if(u.getName()==null){u.send("ERROR\tBạn phải đăng nhập trước");return true;}
        switch(cmd){
            case "JOIN": if(!arg.matches("[\\p{L}\\p{N}_-]{1,20}"))u.send("ERROR\tTên phòng không hợp lệ");else rooms.join(u,arg);break;
            case "LEAVE": rooms.join(u,RoomManager.LOBBY);break;
            case "MSG":if(!arg.isEmpty()&&arg.length()<=4000)rooms.chat(u,clean(arg));else u.send("ERROR\tTin nhắn không hợp lệ");break;
            case "PM":{
                String[] a=arg.split("\\s+",2);if(a.length<2||a[1].isBlank()||a[1].length()>4000)u.send("ERROR\tPM <user> <message>");
                else rooms.privateMessage(u,a[0],clean(a[1]));break;
            }
            case "PMHIST":rooms.privateHistory(u,arg);break;
            case "ROOMS":u.send(rooms.roomsLine());break;
            case "USERS":u.send(rooms.usersLine(u.getRoom()));break;
            case "PEOPLE":rooms.people(u);break;
            case "WATCH":u.setWatching(true);rooms.pushTo(u);break;
            case "FILE":{
                try {
                    String[] a=arg.split(" ",5);
                    if(a.length!=5)throw new IllegalArgumentException();
                    String name=new String(Base64.getDecoder().decode(a[2]),StandardCharsets.UTF_8);
                    String mime=new String(Base64.getDecoder().decode(a[3]),StandardCharsets.UTF_8);
                    byte[] bytes=Base64.getDecoder().decode(a[4]);
                    rooms.file(u,a[0],a[1],name,mime,bytes);
                }catch(Exception e){u.send("ERROR\tFile không hợp lệ");}break;
            }
            case "NICK":u.send("ERROR\tNICK đã thay bằng REGISTER/LOGIN");break;
            default:u.send("ERROR\tLệnh không hợp lệ: "+cmd);
        }
        return true;
    }
    private static String clean(String s){return s.replace('\t',' ').replace('\n',' ').replace('\r',' ');}
}

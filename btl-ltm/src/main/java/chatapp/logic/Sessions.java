package chatapp.logic;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/** Active authenticated browser sessions; tokens cease to work after disconnect. */
public final class Sessions {
  private static final Map<String,String> TOKENS=new ConcurrentHashMap<>();
  private static final SecureRandom RNG=new SecureRandom();
  public static String create(String user){byte[] bytes=new byte[32];RNG.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);TOKENS.put(token,user);return token;}
  public static String user(String token){return TOKENS.get(token);}
  public static void revoke(String token){if(token!=null)TOKENS.remove(token);}
  private Sessions(){}
}

package ru.piaois.rooms.booking;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DemoUsers {
    public enum Role { READER, MEMBER }
    public record User(long id, String login, Role role) {}
    private record Account(User user, byte[] salt, byte[] hash) {}
    private final Map<String, Account> accounts = new HashMap<>();
    public DemoUsers() {
        add(new User(1, "reader", Role.READER), "reader");
        add(new User(2, "member", Role.MEMBER), "member");
    }
    private void add(User user, String password) {
        byte[] salt = new byte[16]; new java.security.SecureRandom().nextBytes(salt);
        accounts.put(user.login(), new Account(user, salt, hash(password, salt)));
    }
    private static byte[] hash(String password, byte[] salt) {
        var spec = new PBEKeySpec(password.toCharArray(), salt, 210000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("Password hashing unavailable", ex); }
        finally { spec.clearPassword(); }
    }
    public User authenticate(String header) {
        try {
            if (header == null || !header.startsWith("Basic ")) throw new IllegalArgumentException();
            String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) throw new IllegalArgumentException();
            Account account = accounts.get(decoded.substring(0, colon));
            if (account != null && MessageDigest.isEqual(account.hash(), hash(decoded.substring(colon + 1), account.salt()))) return account.user();
        } catch (IllegalArgumentException ignored) { }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Нужны верные логин и пароль");
    }
}

package vn.xuantruong.transport.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private static final int N = 16_384;
    private static final int R = 8;
    private static final int P = 1;
    private static final int KEY_LENGTH = 64;
    private final SecureRandom random = new SecureRandom();

    public String newSalt() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String hash(String password, String salt) {
        byte[] derived = SCrypt.generate(
                password.getBytes(StandardCharsets.UTF_8),
                salt.getBytes(StandardCharsets.UTF_8), N, R, P, KEY_LENGTH);
        return HexFormat.of().formatHex(derived);
    }

    public boolean verify(String password, String salt, String expectedHash) {
        try {
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(hash(password, salt)),
                    HexFormat.of().parseHex(expectedHash));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}

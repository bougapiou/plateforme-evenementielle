package bf.evenements.plateforme.auth;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Time-based one-time passwords (RFC 6238: HMAC-SHA1, 30 s steps, 6 digits),
 * compatible with Google Authenticator, Microsoft Authenticator, FreeOTP…
 */
@Component
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    /** Accepted clock drift, in steps, on each side. */
    private static final int WINDOW = 1;

    private final SecureRandom random = new SecureRandom();

    /** New random 160-bit secret, Base32-encoded. */
    public String newSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32(bytes);
    }

    /** {@code otpauth://} URI to show as a QR code in the authenticator app. */
    public String otpauthUri(String secret, String account, String issuer) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=6&period=" + STEP_SECONDS;
    }

    public boolean verify(String secret, String code) {
        return verify(secret, code, Instant.now());
    }

    boolean verify(String secret, String code, Instant at) {
        if (secret == null || code == null) {
            return false;
        }
        String digits = code.replaceAll("\\s", "");
        if (!digits.matches("\\d{6}")) {
            return false;
        }
        byte[] key = unbase32(secret);
        long counter = at.getEpochSecond() / STEP_SECONDS;
        for (int i = -WINDOW; i <= WINDOW; i++) {
            String expected = String.format("%06d", hotp(key, counter + i));
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    digits.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    /** Code for the current step — used by tests. */
    String currentCode(String secret, Instant at) {
        return String.format("%06d", hotp(unbase32(secret), at.getEpochSecond() / STEP_SECONDS));
    }

    private static int hotp(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16)
                    | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return bin % 1_000_000;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    private static byte[] unbase32(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Secret TOTP invalide");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

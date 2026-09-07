package bf.evenements.plateforme.payment.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

/** HMAC-SHA256 helper for signing / verifying payment webhook payloads. */
public final class HmacSignatures {

    private HmacSignatures() {
    }

    public static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de signer le message", e);
        }
    }

    public static boolean matches(String payload, String secret, String providedSignature) {
        if (!StringUtils.hasText(providedSignature)) {
            return false;
        }
        String expected = sign(payload, secret);
        String provided = providedSignature.startsWith("sha256=")
                ? providedSignature.substring(7) : providedSignature;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

package com.idp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class CryptographicAuditSigner {

    private static final String SECRET_KEY = "IDP_ENTERPRISE_AUDIT_HMAC_SECRET_KEY_2026";

    public String generateHmacSignature(String actorId, String action, String targetId, String timestamp) {
        try {
            String payload = String.format("%s:%s:%s:%s", actorId, action, targetId, timestamp);
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(keySpec);
            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to generate HMAC cryptographic audit signature: {}", e.getMessage());
            return "sig-fallback-" + System.currentTimeMillis();
        }
    }
}

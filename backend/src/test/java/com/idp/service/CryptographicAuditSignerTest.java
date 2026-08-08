package com.idp.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CryptographicAuditSignerTest {

    @Test
    public void testHmacGeneration() {
        CryptographicAuditSigner signer = new CryptographicAuditSigner();
        String sig = signer.generateHmacSignature("usr-1", "SECURITY_AUDIT_ACCESS_GRANTED", "srv-petclinic", "2026-08-06 10:00:00");
        assertNotNull(sig);
        assertEquals(64, sig.length());
        System.out.println("TEST_HMAC_SIG_1: " + sig);
    }
}

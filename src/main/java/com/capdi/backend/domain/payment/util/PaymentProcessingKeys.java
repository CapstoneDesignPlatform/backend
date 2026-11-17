package com.capdi.backend.domain.payment.util;

import com.capdi.backend.global.exception.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class PaymentProcessingKeys {
    private PaymentProcessingKeys() { }
    public static String requestKey(String key) {
        if (key==null || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw new CustomException(ErrorCode.INVALID_INPUT);
        return digest(key);
    }
    public static String digest(String text) { return digest(text.getBytes(StandardCharsets.UTF_8)); }
    public static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}

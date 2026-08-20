package com.hotels.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 密码工具类 — PBKDF2WithHmacSHA256 加盐哈希
 * 存储格式: pbkdf2$iterations$saltBase64$hashBase64
 */
public class PasswordUtil {

    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int PBKDF2_KEY_BITS = 256;
    private static final String PREFIX = "pbkdf2$";

    /**
     * 对密码加盐哈希
     */
    public static String hash(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return PREFIX + PBKDF2_ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Logger.getLogger("HotelsX").warning("密码哈希失败: " + e.getMessage());
            return null; // 返回 null 由调用方决定如何处理
        }
    }

    /**
     * 验证密码，兼容旧版明文存储
     */
    public static boolean verify(String plain, String stored) {
        if (stored == null) return false;
        if (stored.startsWith(PREFIX)) {
            try {
                String[] parts = stored.split("\\$");
                if (parts.length != 4) return false;
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                        plain.toCharArray(), salt, iterations, expected.length * 8);
                byte[] actual = skf.generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(expected, actual);
            } catch (Exception e) {
                return false;
            }
        }
        // 旧版明文兼容
        return plain.equals(stored);
    }

    /** 判断存储的密码是否还是明文 */
    public static boolean isPlain(String stored) {
        return stored != null && !stored.startsWith(PREFIX);
    }
}
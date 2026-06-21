package com.alenwifidata.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具 —— 用于设备密码安全存储
 *
 * 加密格式: Base64( IV[12 bytes] + ciphertext + GCM Tag[16 bytes] )
 * GCM 模式提供认证加密 (AEAD)，防止篡改。
 *
 * 密钥配置: aes.secret-key (至少32字符，AES-256)
 */
public final class AESCrypto {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    private static String secretKey;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AESCrypto() {}

    /**
     * Spring 通过 @Value 注入后调用初始化
     */
    public static void init(String key) {
        if (key == null || key.length() < 16) {
            throw new IllegalArgumentException("AES 密钥长度至少 16 字符");
        }
        // 确保密钥为32字节 (AES-256): 不足则补零，过长则截取
        byte[] keyBytes = new byte[32];
        byte[] src = key.getBytes();
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 32));
        secretKey = Base64.getEncoder().encodeToString(keyBytes);
    }

    /**
     * 加密明文，返回 Base64 密文
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        ensureInit();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(
                    Base64.getDecoder().decode(secretKey), ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // IV + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * 解密 Base64 密文，返回明文
     */
    public static String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) return ciphertext;
        // 未加密的旧数据（不以 Base64 合法格式开头）直接返回
        ensureInit();
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext);
            if (data.length < GCM_IV_LENGTH + 1) {
                return ciphertext; // 不是合法的加密格式，视为明文
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            SecretKeySpec keySpec = new SecretKeySpec(
                    Base64.getDecoder().decode(secretKey), ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            return new String(cipher.doFinal(encrypted));
        } catch (IllegalArgumentException e) {
            // Base64 解码失败 → 未加密的旧数据，直接返回
            return ciphertext;
        } catch (Exception e) {
            logWarning("AES 解密失败，返回原始值: {}", e.getMessage());
            return ciphertext;
        }
    }

    private static void ensureInit() {
        if (secretKey == null) {
            throw new IllegalStateException("AESCrypto 未初始化, 请配置 aes.secret-key");
        }
    }

    // 避免循环依赖 @Slf4j
    private static void logWarning(String fmt, Object... args) {
        System.err.printf("[AESCrypto] " + fmt + "%n", args);
    }
}

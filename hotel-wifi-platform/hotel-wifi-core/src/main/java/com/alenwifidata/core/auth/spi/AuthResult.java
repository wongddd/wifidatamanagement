package com.alenwifidata.core.auth.spi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 认证结果
 */
@Data
@AllArgsConstructor
public class AuthResult {

    private boolean success;
    private String userId;
    private String username;
    private String message;

    public static AuthResult success(String userId, String username) {
        return new AuthResult(true, userId, username, null);
    }

    public static AuthResult fail(String message) {
        return new AuthResult(false, null, null, message);
    }
}

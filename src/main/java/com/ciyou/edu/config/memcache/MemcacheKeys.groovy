package com.ciyou.edu.config.memcache

import java.security.MessageDigest

/**
 * Helpers for building memcached keys from request-derived names.
 * A memcached key must be 1..250 bytes with no whitespace or control characters.
 */
class MemcacheKeys {

    static final String NAMESPACE = "ciyou:v1:"
    static final int MAX_KEY_LENGTH = 250

    /**
     * Rejects names that contain whitespace/control characters or exceed the length cap, then
     * returns the namespaced SHA-256 hex of the name.
     */
    static String safeKey(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("invalid cache key length")
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i)
            if (c <= 0x20 || c == 0x7f) {
                throw new IllegalArgumentException("invalid character in cache key")
            }
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes("UTF-8"))
        StringBuilder hex = new StringBuilder(NAMESPACE)
        for (byte b : digest) {
            hex.append(String.format("%02x", b))
        }
        return hex.toString()
    }

    /**
     * True for keys that are already safe to put on a memcached command line.
     */
    static boolean isProtocolSafe(String key) {
        return key != null && key ==~ /[A-Za-z0-9:_.-]{1,250}/
    }
}

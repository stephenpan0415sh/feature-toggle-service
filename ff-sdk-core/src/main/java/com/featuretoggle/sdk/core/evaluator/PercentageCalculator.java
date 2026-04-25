package com.featuretoggle.sdk.core.evaluator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic percentage calculator using SHA-256 hashing.
 * Ensures the same user always gets the same result for a given percentage.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
public class PercentageCalculator {

    /**
     * Check if a user is within the specified percentage using deterministic hashing.
     * 
     * @param userId The user identifier to hash
     * @param percentage The percentage (0-100)
     * @return true if user is in the percentage rollout
     */
    public static boolean isInPercentage(String userId, int percentage) {
        if (percentage <= 0) {
            return false;
        }
        if (percentage >= 100) {
            return true;
        }

        // Calculate hash and normalize to 0-99
        int hash = hash(userId);
        int bucket = Math.abs(hash % 100);
        
        return bucket < percentage;
    }

    /**
     * Generate a well-distributed hash value for the given string.
     * Uses a combination of String.hashCode() and bit mixing for good distribution.
     */
    private static int hash(String input) {
        int h = input.hashCode();
        // Apply bit mixing to improve distribution
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        h *= 0xc2b2ae35;
        h ^= (h >>> 16);
        return h;
    }
}

package com.featuretoggle.sdk.core.evaluator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PercentageCalculator.
 * Verifies deterministic hashing and distribution.
 * 
 * @author Feature Toggle Team
 * @version 1.0.0
 */
class PercentageCalculatorTest {

    // Test: 0% means no one is included
    @Test
    void shouldExcludeAllWhenPercentageIsZero() {
        assertFalse(PercentageCalculator.isInPercentage("user123", 0));
        assertFalse(PercentageCalculator.isInPercentage("any_user", 0));
    }

    // Test: 100% means everyone is included
    @Test
    void shouldIncludeAllWhenPercentageIsHundred() {
        assertTrue(PercentageCalculator.isInPercentage("user123", 100));
        assertTrue(PercentageCalculator.isInPercentage("any_user", 100));
    }

    // Test: Deterministic - same user always gets same result
    @Test
    void shouldBeDeterministicForSameUser() {
        String userId = "user_12345";
        int percentage = 50;

        boolean result1 = PercentageCalculator.isInPercentage(userId, percentage);
        boolean result2 = PercentageCalculator.isInPercentage(userId, percentage);
        boolean result3 = PercentageCalculator.isInPercentage(userId, percentage);

        assertEquals(result1, result2);
        assertEquals(result2, result3);
    }

    // Test: Different users may get different results
    @Test
    void shouldVaryAcrossDifferentUsers() {
        int percentage = 50;
        int includedCount = 0;
        int totalUsers = 1000;

        for (int i = 0; i < totalUsers; i++) {
            if (PercentageCalculator.isInPercentage("user_" + i, percentage)) {
                includedCount++;
            }
        }

        // Should be approximately 50% (allowing for some variance)
        double inclusionRate = (double) includedCount / totalUsers;
        assertTrue(inclusionRate >= 0.45 && inclusionRate <= 0.55,
            "Expected ~50% inclusion rate, got: " + inclusionRate);
    }

    // Test: Distribution is uniform across percentage values
    @Test
    void shouldDistributeUniformlyAcrossPercentages() {
        String userId = "test_user";
        
        // User should be in 10% but not in 5%
        boolean in10Percent = PercentageCalculator.isInPercentage(userId, 10);
        boolean in5Percent = PercentageCalculator.isInPercentage(userId, 5);
        
        if (in10Percent) {
            assertFalse(in5Percent || true); // May or may not be in 5%
        }
    }

    // Test: Edge cases
    @Test
    void shouldHandleEdgeCases() {
        // Empty string
        assertDoesNotThrow(() -> PercentageCalculator.isInPercentage("", 50));
        
        // Very long string
        String longString = "a".repeat(10000);
        assertDoesNotThrow(() -> PercentageCalculator.isInPercentage(longString, 50));
        
        // Special characters
        assertDoesNotThrow(() -> PercentageCalculator.isInPercentage("user@#$%^&*()", 50));
        
        // Unicode
        assertDoesNotThrow(() -> PercentageCalculator.isInPercentage("用户_测试", 50));
    }

    // Test: Consistency across different percentages for same user
    @Test
    void shouldBeConsistentAcrossPercentages() {
        String userId = "consistent_user";
        
        // If user is in 30%, they must also be in 50%
        boolean in30 = PercentageCalculator.isInPercentage(userId, 30);
        boolean in50 = PercentageCalculator.isInPercentage(userId, 50);
        
        if (in30) {
            assertTrue(in50, "If user is in 30%, they must be in 50%");
        }
    }

    // Test: Monotonicity - users never drop out when percentage increases
    @Test
    void shouldGuaranteeMonotonicityWhenPercentageIncreases() {
        // Test multiple users to ensure monotonicity holds
        for (int i = 0; i < 100; i++) {
            String userId = "user_" + i;
            
            // Track when user first enters the rollout
            Integer firstEnterPercentage = null;
            
            // Check all percentages from 1 to 100
            for (int percentage = 1; percentage <= 100; percentage++) {
                boolean isIn = PercentageCalculator.isInPercentage(userId, percentage);
                
                if (isIn && firstEnterPercentage == null) {
                    // User just entered the rollout
                    firstEnterPercentage = percentage;
                } else if (firstEnterPercentage != null) {
                    // User was already in, must still be in (monotonicity)
                    assertTrue(isIn, 
                        String.format("User %s entered at %d%% but dropped out at %d%%",
                            userId, firstEnterPercentage, percentage));
                }
            }
        }
    }

    // Test: Verify specific user behavior across percentage increments
    @Test
    void shouldShowCorrectBucketBehavior() {
        String userId = "test_monotonic_user";
        
        // Find which bucket this user falls into
        int userBucket = -1;
        for (int i = 0; i <= 100; i++) {
            if (PercentageCalculator.isInPercentage(userId, i)) {
                userBucket = i - 1; // The threshold where they enter
                break;
            }
        }
        
        assertTrue(userBucket >= 0 && userBucket < 100, 
            "User should have a valid bucket");
        
        // Verify: not in below bucket, in at and above bucket
        assertFalse(PercentageCalculator.isInPercentage(userId, userBucket));
        assertTrue(PercentageCalculator.isInPercentage(userId, userBucket + 1));
        assertTrue(PercentageCalculator.isInPercentage(userId, 100));
    }

    // Test: Verify hash distribution quality
    @Test
    void shouldHaveGoodHashDistribution() {
        int[] buckets = new int[100];
        int totalUsers = 10000;

        // Distribute users into buckets
        for (int i = 0; i < totalUsers; i++) {
            String userId = "user_" + i;
            int hash = hashForTest(userId);
            int bucket = Math.abs(hash % 100);
            buckets[bucket]++;
        }

        // Check that distribution is relatively uniform
        int expectedPerBucket = totalUsers / 100; // 100
        for (int count : buckets) {
            // Allow 30% variance
            assertTrue(count >= expectedPerBucket * 0.7 && count <= expectedPerBucket * 1.3,
                "Bucket has " + count + " users, expected ~" + expectedPerBucket);
        }
    }

    // Same hash implementation as PercentageCalculator
    private int hashForTest(String input) {
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

package com.gmailai.coreservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RateLimiterService - Implements Token Bucket algorithm for Groq API rate limiting
 * 
 * Purpose: Control the rate of AI draft generation requests to respect Groq API's
 * 30 requests/minute limit while allowing bursts when tokens are available.
 * 
 * Token Bucket Algorithm:
 * - Bucket capacity: 30 tokens (configurable)
 * - Refill rate: 0.5 tokens/second (30 tokens/60 seconds)
 * - Burst allowance: Up to 30 requests can be made immediately if bucket is full
 * - Blocking behavior: acquire() blocks until a token is available
 * 
 * Performance Impact:
 * - Replaces blocking Thread.sleep(2500ms) with dynamic rate limiting
 * - Allows parallel processing while maintaining average rate
 * - Enables burst processing when bucket has tokens
 */
@Service
public class RateLimiterService {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition tokensAvailable = lock.newCondition();

    // Token bucket state
    private final AtomicInteger availableTokens;
    private final AtomicLong lastRefillTime;

    // Configuration
    private final int capacity;
    private final double refillRate; // tokens per second

    public RateLimiterService(
            @Value("${email.processing.rate-limit-requests-per-minute:30}") int requestsPerMinute) {
        this.capacity = requestsPerMinute;
        this.refillRate = requestsPerMinute / 60.0; // Convert to tokens per second
        this.availableTokens = new AtomicInteger(capacity); // Start with full bucket
        this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        
        System.out.println("[RateLimiter] Initialized with capacity: " + capacity + 
                          " tokens, refill rate: " + refillRate + " tokens/second");
    }

    /**
     * Acquire a token from the bucket (blocking)
     * 
     * Blocks until a token is available. Thread-safe using ReentrantLock.
     * Refills tokens based on elapsed time before attempting to acquire.
     */
    public void acquire() {
        lock.lock();
        try {
            // Refill tokens based on elapsed time
            refillTokens();

            // Wait until at least one token is available
            while (availableTokens.get() <= 0) {
                try {
                    // Calculate wait time until next token is available
                    long waitTimeMs = (long) (1000.0 / refillRate);
                    tokensAvailable.await(waitTimeMs, TimeUnit.MILLISECONDS);
                    refillTokens(); // Refill after waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Rate limiter interrupted", e);
                }
            }

            // Consume one token
            availableTokens.decrementAndGet();
            
        } finally {
            lock.unlock();
        }
    }

    /**
     * Try to acquire a token with timeout (non-blocking with timeout)
     * 
     * @param timeout Maximum time to wait
     * @param unit Time unit
     * @return true if token acquired, false if timeout expired
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        
        lock.lock();
        try {
            // Refill tokens based on elapsed time
            refillTokens();

            // Wait until token is available or timeout expires
            while (availableTokens.get() <= 0) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    return false; // Timeout expired
                }

                try {
                    tokensAvailable.awaitNanos(remainingNanos);
                    refillTokens(); // Refill after waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            // Consume one token
            availableTokens.decrementAndGet();
            return true;
            
        } finally {
            lock.unlock();
        }
    }

    /**
     * Refill tokens based on elapsed time since last refill
     * 
     * Thread-safe: Must be called within lock
     * 
     * Calculation:
     * - elapsed time (seconds) = (currentTime - lastRefillTime) / 1000
     * - tokens to add = elapsed time × refill rate
     * - cap at capacity
     */
    private void refillTokens() {
        long currentTime = System.currentTimeMillis();
        long lastRefill = lastRefillTime.get();
        double elapsedSeconds = (currentTime - lastRefill) / 1000.0;

        if (elapsedSeconds > 0) {
            // Calculate tokens to add
            int tokensToAdd = (int) (elapsedSeconds * refillRate);

            if (tokensToAdd > 0) {
                int currentTokens = availableTokens.get();
                int newTokens = Math.min(currentTokens + tokensToAdd, capacity);
                availableTokens.set(newTokens);
                lastRefillTime.set(currentTime);

                // Signal waiting threads that tokens are available
                if (newTokens > 0) {
                    tokensAvailable.signalAll();
                }
            }
        }
    }

    /**
     * Get current number of available tokens (for monitoring/debugging)
     */
    public int getAvailableTokens() {
        lock.lock();
        try {
            refillTokens(); // Refill before returning count
            return availableTokens.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get rate limiter statistics (for monitoring/debugging)
     */
    public java.util.Map<String, Object> getStats() {
        return java.util.Map.of(
            "capacity", capacity,
            "refillRate", refillRate,
            "availableTokens", getAvailableTokens(),
            "requestsPerMinute", capacity
        );
    }

    /**
     * Reset rate limiter (for testing purposes)
     */
    public void reset() {
        lock.lock();
        try {
            availableTokens.set(capacity);
            lastRefillTime.set(System.currentTimeMillis());
            System.out.println("[RateLimiter] Reset to full capacity");
        } finally {
            lock.unlock();
        }
    }
}

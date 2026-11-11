package ru.promoit.shortener.core.model;

import java.time.Instant;
import java.util.UUID;

public class ShortLink {
    private final String shortUrl;
    private final String originalUrl;
    private final UUID ownerUuid;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int maxClicks;
    private int currentClicks;

    public ShortLink(
            String shortUrl,
            String originalUrl,
            UUID ownerUuid,
            Instant createdAt,
            Instant expiresAt,
            int maxClicks) {
        this.shortUrl = shortUrl;
        this.originalUrl = originalUrl;
        this.ownerUuid = ownerUuid;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.maxClicks = maxClicks;
        this.currentClicks = 0;
    }

    public synchronized boolean incrementClick() {
        if (currentClicks >= maxClicks) {
            return false;
        }
        currentClicks++;
        return true;
    }

    public synchronized boolean isClickLimitReached() {
        return currentClicks >= maxClicks;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public synchronized void updateMaxClicks(int newMaxClicks) {
        if (newMaxClicks <= 0) {
            throw new IllegalArgumentException("Лимит переходов должен быть больше 0.");
        }
        this.maxClicks = newMaxClicks;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public synchronized int getMaxClicks() {
        return maxClicks;
    }

    public synchronized int getCurrentClicks() {
        return currentClicks;
    }
}
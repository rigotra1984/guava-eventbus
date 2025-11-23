package com.rigoberto.pr.Models;

public class StoredEvent {
    private Long id;
    private String type;
    private String payload;
    private String status;
    private int attempts;
    private int maxAttempts;

    public StoredEvent(Long id, String type, String payload, String status, int attempts, int maxAttempts) {
        this.id = id;
        this.type = type;
        this.payload = payload;
        this.status = status;
        this.attempts = attempts;
        this.maxAttempts = maxAttempts;
    }

    public StoredEvent(Long id, String type, String payload, int attempts, int maxAttempts) {
        this.id = id;
        this.type = type;
        this.payload = payload;
        this.attempts = attempts;
        this.maxAttempts = maxAttempts;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
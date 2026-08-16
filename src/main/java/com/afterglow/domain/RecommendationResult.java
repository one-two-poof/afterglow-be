package com.afterglow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "recommendation_results")
public class RecommendationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 512)
    private String message;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    protected RecommendationResult() {
    }

    public RecommendationResult(Long userId, String status, String message, Instant requestedAt) {
        this.userId = userId;
        this.status = status;
        this.message = message;
        this.requestedAt = requestedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getRequestedAt() { return requestedAt; }
}

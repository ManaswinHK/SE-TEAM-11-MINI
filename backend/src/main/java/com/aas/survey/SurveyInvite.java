package com.aas.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

/** The one-time exit-survey link for a separation (AAS-F-030). */
@Entity
public class SurveyInvite {

    public static final int VALID_DAYS = 30;
    public static final int MAX_REMINDERS = 2;
    public static final int REMINDER_INTERVAL_DAYS = 3;

    public enum Status { PENDING, SENT, SUBMITTED, EXPIRED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private Long separationId;

    @Column(nullable = false)
    private String token;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime sentAt;
    private LocalDateTime lastReminderAt;
    private int remindersSent;
    private LocalDateTime submittedAt;
    private LocalDateTime cancelledAt;

    protected SurveyInvite() {
    }

    public SurveyInvite(Long orgId, Long separationId, String token, LocalDateTime createdAt) {
        this.orgId = orgId;
        this.separationId = separationId;
        this.token = token;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plusDays(VALID_DAYS);
    }

    public Status status(LocalDateTime now) {
        if (submittedAt != null) return Status.SUBMITTED;
        if (cancelledAt != null) return Status.CANCELLED;
        if (!expiresAt.isAfter(now)) return Status.EXPIRED;
        return sentAt == null ? Status.PENDING : Status.SENT;
    }

    /** AAS-F-032: a reminder goes out 3 days after the invite or the previous reminder, at most twice. */
    public boolean reminderDue(LocalDateTime now) {
        LocalDateTime last = lastReminderAt != null ? lastReminderAt : sentAt;
        return remindersSent < MAX_REMINDERS && !last.plusDays(REMINDER_INTERVAL_DAYS).isAfter(now);
    }

    public void markReminded(LocalDateTime when) {
        this.lastReminderAt = when;
        this.remindersSent++;
    }

    public void markSent(LocalDateTime when) { this.sentAt = when; }
    public void markSubmitted(LocalDateTime when) { this.submittedAt = when; }
    public void cancel(LocalDateTime when) { this.cancelledAt = when; }

    public Long getId() { return id; }
    public Long getOrgId() { return orgId; }
    public Long getSeparationId() { return separationId; }
    public String getToken() { return token; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public int getRemindersSent() { return remindersSent; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
}

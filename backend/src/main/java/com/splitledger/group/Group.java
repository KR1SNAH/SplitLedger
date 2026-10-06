package com.splitledger.group;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.splitledger.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    /** ISO 4217 code the group's expenses are recorded in. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Null if the creator's account was deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    /** SHA-256 of the shareable invite token; null when there is no invite link. */
    @Column(name = "invite_token_hash", length = 64)
    private String inviteTokenHash;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Group(User createdBy) {
        this.createdBy = createdBy;
    }

    public boolean hasActiveInviteLink(Instant now) {
        return inviteTokenHash != null && inviteExpiresAt.isAfter(now);
    }

    /** Replaces any existing link, so previously shared links stop working. */
    public void setInviteLink(String tokenHash, Instant expiresAt) {
        this.inviteTokenHash = tokenHash;
        this.inviteExpiresAt = expiresAt;
    }

    public void clearInviteLink() {
        setInviteLink(null, null);
    }
}

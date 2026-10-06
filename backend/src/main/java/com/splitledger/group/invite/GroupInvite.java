package com.splitledger.group.invite;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.splitledger.group.Group;
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

/** A pending invite for an email address. Deleted once it's accepted, declined or cancelled. */
@Entity
@Table(name = "group_invites")
@Getter
@NoArgsConstructor
public class GroupInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private Group group;

    /** Stored lowercase, like users.email. */
    @Column(nullable = false, updatable = false)
    private String email;

    /** Null if the inviter's account was deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", updatable = false)
    private User invitedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GroupInvite(Group group, String email, User invitedBy) {
        this.group = group;
        this.email = email;
        this.invitedBy = invitedBy;
    }
}

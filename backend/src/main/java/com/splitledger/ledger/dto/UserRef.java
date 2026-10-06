package com.splitledger.ledger.dto;

import java.util.UUID;

import com.splitledger.user.User;

/** A person as shown next to money: who paid, who owes, who settled. */
public record UserRef(UUID userId, String displayName) {

    /** Null-safe, for references like "created by" whose account may have been deleted. */
    public static UserRef from(User user) {
        return user == null ? null : new UserRef(user.getId(), user.getDisplayName());
    }
}

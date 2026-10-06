package com.splitledger.ledger;

import java.math.BigDecimal;
import java.util.UUID;

/** A per-group total for one user from an aggregate query. Built directly by JPQL constructor expressions. */
public record GroupAmount(UUID groupId, BigDecimal amount) {
}

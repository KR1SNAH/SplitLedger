package com.splitledger.ledger;

import java.math.BigDecimal;
import java.util.UUID;

/** A per-user total from an aggregate query. Built directly by JPQL constructor expressions. */
public record UserAmount(UUID userId, BigDecimal amount) {
}

package com.mitra.taskrequests;

/**
 * Lifecycle states for a TaskRequest (customer-posted job).
 *
 * State machine:
 * OPEN → QUOTING (first quote arrives)
 * QUOTING → ACCEPTED (customer accepts a quote)
 * ACCEPTED → STARTED (OTP verified by provider)
 * STARTED → COMPLETED (provider marks done)
 * OPEN/QUOTING/ACCEPTED → CANCELLED (customer or admin)
 * OPEN/QUOTING → EXPIRED (future: after N days with no accepted quote)
 */
public enum TaskRequestStatus {
    /** Task posted, no quotes yet. Visible to nearby providers. */
    OPEN,

    /** At least one quote received. Customer is reviewing quotes. */
    QUOTING,

    /** Customer accepted a quote. Waiting for job to start. */
    ACCEPTED,

    /** Provider verified OTP. Job is in progress. */
    STARTED,

    /** Job done. Earnings credited. Points awarded. */
    COMPLETED,

    /** Cancelled by customer, provider, or admin. */
    CANCELLED,

    /** No accepted quote within allowed window (future feature). */
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == EXPIRED;
    }

    public boolean allowsNewQuotes() {
        return this == OPEN || this == QUOTING;
    }

    public boolean isChatAllowed() {
        return this == ACCEPTED || this == STARTED;
    }
}

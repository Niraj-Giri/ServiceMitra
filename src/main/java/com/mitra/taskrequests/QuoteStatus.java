package com.mitra.taskrequests;

/**
 * Lifecycle states for a Quote submitted by a provider.
 *
 * State machine:
 * PENDING → ACCEPTED          (customer directly accepts)
 * PENDING → COUNTER_OFFERED   (customer makes a counter-offer)
 * COUNTER_OFFERED → ACCEPTED  (provider agrees to counter price)
 * COUNTER_OFFERED → REJECTED  (provider rejects counter → quote ends)
 * PENDING → WITHDRAWN         (provider takes back their quote)
 * PENDING/COUNTER_OFFERED → REJECTED  (when another quote is accepted)
 * PENDING/COUNTER_OFFERED → EXPIRED   (future: time-based expiry)
 */
public enum QuoteStatus {
    /** Provider submitted, waiting for customer action. */
    PENDING,

    /** Customer sent a counter-offer. Waiting for provider response. */
    COUNTER_OFFERED,

    /** Quote accepted (either directly or after counter). */
    ACCEPTED,

    /** Quote rejected (customer chose another, or provider rejected counter). */
    REJECTED,

    /** Provider withdrew their own quote. */
    WITHDRAWN,

    /** Quote expired (future: time-based). */
    EXPIRED;

    public boolean isActive() {
        return this == PENDING || this == COUNTER_OFFERED;
    }

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == WITHDRAWN || this == EXPIRED;
    }
}

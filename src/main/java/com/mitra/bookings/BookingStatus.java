package com.mitra.bookings;

/**
 * Booking lifecycle state machine.
 *
 * PENDING_DISPATCH → ASSIGNED → ACCEPTED → STARTED → COMPLETED
 *                                        ↓          ↓
 *                            CANCELLED_BY_CUSTOMER  CANCELLED_BY_CUSTOMER
 *                            CANCELLED_BY_PROVIDER  (not allowed in STARTED)
 */
public enum BookingStatus {

    /**
     * Booking created, no provider assigned yet.
     */
    PENDING_DISPATCH,

    /**
     * System has assigned a provider. Waiting for provider to accept/reject.
     */
    ASSIGNED,

    /**
     * Provider accepted. OTP has been generated and sent to customer.
     */
    ACCEPTED,

    /**
     * Customer showed OTP, provider verified it. Job is in progress.
     */
    STARTED,

    /**
     * Provider marked the job as done. Earnings credited.
     */
    COMPLETED,

    /**
     * Customer cancelled before job started.
     */
    CANCELLED_BY_CUSTOMER,

    /**
     * Provider cancelled or did not show up.
     */
    CANCELLED_BY_PROVIDER,

    /**
     * Admin cancelled (emergency or dispute).
     */
    CANCELLED_BY_ADMIN;

    /**
     * Returns true if this booking is in a terminal (final) state.
     * Chat and modifications are disabled for terminal bookings.
     */
    public boolean isTerminal() {
        return this == COMPLETED
                || this == CANCELLED_BY_CUSTOMER
                || this == CANCELLED_BY_PROVIDER
                || this == CANCELLED_BY_ADMIN;
    }

    /**
     * Returns true if chat should be allowed for this booking.
     */
    public boolean isChatAllowed() {
        return this == ASSIGNED || this == ACCEPTED || this == STARTED;
    }
}

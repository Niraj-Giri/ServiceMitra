package com.mitra.taskrequests;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    /** All quotes for a task, oldest first (chronological for display). */
    List<Quote> findByTaskRequestIdOrderByCreatedAtAsc(Long taskRequestId);

    /** All quotes submitted by a specific provider, newest first. */
    List<Quote> findByProviderIdOrderByCreatedAtDesc(Long providerId);

    /** Active quotes submitted by a provider (non-terminal). */
    List<Quote> findByProviderIdAndStatusIn(Long providerId, List<QuoteStatus> statuses);

    /** Check if provider already has a non-withdrawn quote for this task. */
    boolean existsByTaskRequestIdAndProviderIdAndStatusNotIn(
        Long taskRequestId, Long providerId, List<QuoteStatus> excludedStatuses
    );

    /** Find a specific provider's quote for a task. */
    Optional<Quote> findByTaskRequestIdAndProviderId(Long taskRequestId, Long providerId);

    /** All non-terminal (active) quotes for a task — used when accepting one to reject others. */
    List<Quote> findByTaskRequestIdAndStatusIn(Long taskRequestId, List<QuoteStatus> statuses);
}

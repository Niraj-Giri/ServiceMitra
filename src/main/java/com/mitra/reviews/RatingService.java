package com.mitra.reviews;

import com.mitra.common.BadRequestException;
import com.mitra.common.ForbiddenException;
import com.mitra.common.ResourceNotFoundException;
import com.mitra.taskrequests.TaskRequest;
import com.mitra.taskrequests.TaskRequestRepository;
import com.mitra.taskrequests.TaskRequestStatus;
import com.mitra.users.Provider;
import com.mitra.users.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final TaskRequestRepository taskRequestRepository;
    private final ProviderRepository providerRepository;

    /**
     * Submits a rating for a completed booking/task.
     *
     * Rules:
     * 1. Only customer of the booking can rate
     * 2. Booking must be COMPLETED
     * 3. One rating per booking (idempotency check)
     * 4. Score must be 1-5 for each dimension
     * 5. After save, provider's rating_cache is recalculated
     */
    @Transactional
    public Rating submitRating(Long customerId, Long bookingId,
                               int punctuality, int quality, int behavior,
                               String comment) {
        // Validate scores
        validateScore(punctuality, "Punctuality");
        validateScore(quality, "Quality");
        validateScore(behavior, "Behavior");

        // Get task request and verify ownership + status
        TaskRequest task = taskRequestRepository.findById(bookingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", bookingId));

        if (task.getUser() == null || !task.getUser().getId().equals(customerId)) {
            throw ForbiddenException.notYourResource("booking");
        }

        if (task.getStatus() != TaskRequestStatus.COMPLETED) {
            throw new BadRequestException("You can only rate a completed booking.");
        }

        var acceptedQuote = task.getQuotes().stream()
                .filter(q -> q.getId().equals(task.getAcceptedQuoteId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Cannot rate a booking with no assigned provider."));

        if (acceptedQuote.getProvider() == null) {
            throw new BadRequestException("Cannot rate a booking with no assigned provider.");
        }

        // Prevent duplicate ratings
        if (ratingRepository.existsByBookingId(bookingId)) {
            throw new BadRequestException("You have already rated this booking.");
        }

        BigDecimal overall = Rating.calculateOverall(punctuality, quality, behavior);

        Rating rating = Rating.builder()
                .bookingId(bookingId)
                .customerId(customerId)
                .providerId(acceptedQuote.getProvider().getId())
                .punctualityScore(punctuality)
                .qualityScore(quality)
                .behaviorScore(behavior)
                .overallScore(overall)
                .comment(comment)
                .isVisible(true)
                .build();

        rating = ratingRepository.save(rating);

        // Recalculate provider's rating average
        updateProviderRatingCache(acceptedQuote.getProvider().getId());

        log.info("Rating submitted for booking {} → provider {}. Overall: {}",
                bookingId, acceptedQuote.getProvider().getId(), overall);

        return rating;
    }

    /**
     * Returns all visible ratings for a provider (public endpoint).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getProviderRatings(Long providerId) {
        return ratingRepository
                .findByProviderIdAndIsVisibleTrueOrderByCreatedAtDesc(providerId)
                .stream()
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "overallScore", r.getOverallScore(),
                        "punctualityScore", r.getPunctualityScore(),
                        "qualityScore", r.getQualityScore(),
                        "behaviorScore", r.getBehaviorScore(),
                        "comment", r.getComment() != null ? r.getComment() : "",
                        "createdAt", r.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Rating getRatingByBookingId(Long customerId, Long bookingId) {
        Rating rating = ratingRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Rating not found for booking ID: " + bookingId));
        if (!rating.getCustomerId().equals(customerId)) {
            throw ForbiddenException.notYourResource("rating");
        }
        return rating;
    }

    @Transactional
    public Rating updateRating(Long customerId, Long bookingId,
                               int punctuality, int quality, int behavior,
                               String comment) {
        throw new BadRequestException("Reviews cannot be modified once they are submitted!");
    }

    /**
     * Recalculates and updates the provider's cached average rating.
     * Called after every new rating submission.
     */
    private void updateProviderRatingCache(Long providerId) {
        Provider provider = providerRepository.findById(providerId).orElse(null);
        if (provider == null) return;

        BigDecimal avg = ratingRepository.calculateAverageRating(providerId)
                .orElse(BigDecimal.ZERO);
        provider.setRatingCache(avg.setScale(2, java.math.RoundingMode.HALF_UP));
        providerRepository.save(provider);

        log.info("Provider {} rating cache updated to {}", providerId, avg);
    }

    private void validateScore(int score, String field) {
        if (score < 1 || score > 5) {
            throw new BadRequestException(field + " score must be between 1 and 5.");
        }
    }
}

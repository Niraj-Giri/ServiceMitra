package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.common.ApiResponse;
import com.mitra.reviews.Rating;
import com.mitra.reviews.RatingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RatingController {

    private final RatingService ratingService;
    private final AuthService authService;

    /**
     * POST /api/v1/ratings
     * Customer submits a rating after job completion.
     */
    @PostMapping("/ratings")
    public ResponseEntity<ApiResponse<Rating>> submitRating(
            HttpServletRequest httpRequest,
            @Valid @RequestBody SubmitRatingRequest request) {

        Long customerId = authService.extractUserIdFromToken(httpRequest);
        Rating rating = ratingService.submitRating(
                customerId,
                request.getBookingId(),
                request.getPunctualityScore(),
                request.getQualityScore(),
                request.getBehaviorScore(),
                request.getComment()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(rating, "Thank you for your feedback!"));
    }

    /**
     * GET /api/v1/providers/{id}/ratings
     * Public endpoint — get all visible ratings for a provider.
     */
    @GetMapping("/providers/{id}/ratings")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProviderRatings(
            @PathVariable Long id) {
        List<Map<String, Object>> ratings = ratingService.getProviderRatings(id);
        return ResponseEntity.ok(ApiResponse.success(ratings));
    }

    @Data
    public static class SubmitRatingRequest {
        @NotNull(message = "Booking ID is required")
        private Long bookingId;

        @NotNull @Min(1) @Max(5)
        private Integer punctualityScore;

        @NotNull @Min(1) @Max(5)
        private Integer qualityScore;

        @NotNull @Min(1) @Max(5)
        private Integer behaviorScore;

        private String comment;
    }
}

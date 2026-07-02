package com.mitra.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import com.mitra.entity.Review;
import com.mitra.entity.ReviewRepository;
import java.util.*;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "*")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @PostMapping("/create")
    public ResponseEntity<?> createReview(@RequestBody Map<String, Object> request) {
        Review review = new Review();
        review.setBookingId(Long.valueOf(request.get("bookingId").toString()));
        review.setCustomerId(Long.valueOf(request.get("customerId").toString()));
        review.setProviderId(Long.valueOf(request.get("providerId").toString()));
        review.setRating(Integer.parseInt(request.get("rating").toString()));
        review.setComment(request.get("comment").toString());
        
        reviewRepository.save(review);
                
        return ResponseEntity.ok(Collections.singletonMap("status", "success"));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getProviderReviews(@PathVariable Long providerId) {
        List<Review> reviews = reviewRepository.findByProviderId(providerId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/provider/{providerId}/average")
    public ResponseEntity<?> getAverageRating(@PathVariable Long providerId) {
        List<Review> reviews = reviewRepository.findByProviderId(providerId);
        if (reviews.isEmpty()) {
            return ResponseEntity.ok(Collections.singletonMap("average", 0.0));
        }
        
        double sum = 0;
        for (Review r : reviews) {
            sum += r.getRating();
        }
        double avg = sum / reviews.size();
        
        return ResponseEntity.ok(Collections.singletonMap("average", avg));
    }
}

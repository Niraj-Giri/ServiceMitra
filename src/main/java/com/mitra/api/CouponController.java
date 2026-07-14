package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.bookings.Coupon;
import com.mitra.bookings.CouponRepository;
import com.mitra.bookings.CouponUsageRepository;
import com.mitra.common.ApiResponse;
import com.mitra.common.BadRequestException;
import com.mitra.common.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
// SEC-05: @CrossOrigin removed - CORS is centrally managed in SecurityConfig
public class CouponController {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final AuthService authService;

    // -----------------------------------------------------------------------------
    // ADMIN ENDPOINTS
    // -----------------------------------------------------------------------------

    @PostMapping("/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Coupon>> createCoupon(@RequestBody Coupon coupon) {
        if (coupon.getCode() == null || coupon.getCode().isBlank()) {
            throw new BadRequestException("Coupon code is required");
        }
        coupon.setCode(coupon.getCode().trim().toUpperCase());
        if (couponRepository.findByCodeIgnoreCase(coupon.getCode()).isPresent()) {
            throw new BadRequestException("A coupon with code '" + coupon.getCode() + "' already exists.");
        }
        if (coupon.getDiscountType() == null || (!coupon.getDiscountType().equals("FLAT") && !coupon.getDiscountType().equals("PERCENTAGE"))) {
            throw new BadRequestException("Discount type must be FLAT or PERCENTAGE");
        }
        if (coupon.getDiscountValue() == null || coupon.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Discount value must be greater than zero");
        }
        if (coupon.getStartDate() == null || coupon.getExpiryDate() == null) {
            throw new BadRequestException("Start date and expiry date are required");
        }
        if (coupon.getStartDate().isAfter(coupon.getExpiryDate())) {
            throw new BadRequestException("Start date cannot be after expiry date");
        }

        Coupon saved = couponRepository.save(coupon);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(saved, "Coupon created successfully"));
    }

    @GetMapping("/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Coupon>>> getAllCoupons() {
        return ResponseEntity.ok(ApiResponse.success(couponRepository.findAll()));
    }

    @GetMapping("/admin/coupons/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Coupon>> getCouponById(@PathVariable Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Coupon", id));
        return ResponseEntity.ok(ApiResponse.success(coupon));
    }

    @PutMapping("/admin/coupons/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Coupon>> updateCoupon(@PathVariable Long id, @RequestBody Coupon updated) {
        Coupon existing = couponRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Coupon", id));

        if (updated.getCode() != null && !updated.getCode().isBlank()) {
            String newCode = updated.getCode().trim().toUpperCase();
            if (!newCode.equals(existing.getCode()) && couponRepository.findByCodeIgnoreCase(newCode).isPresent()) {
                throw new BadRequestException("A coupon with code '" + newCode + "' already exists.");
            }
            existing.setCode(newCode);
        }
        if (updated.getDiscountType() != null) {
            if (!updated.getDiscountType().equals("FLAT") && !updated.getDiscountType().equals("PERCENTAGE")) {
                throw new BadRequestException("Discount type must be FLAT or PERCENTAGE");
            }
            existing.setDiscountType(updated.getDiscountType());
        }
        if (updated.getDiscountValue() != null) {
            if (updated.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Discount value must be greater than zero");
            }
            existing.setDiscountValue(updated.getDiscountValue());
        }
        if (updated.getMinBookingAmount() != null) {
            existing.setMinBookingAmount(updated.getMinBookingAmount());
        }
        if (updated.getMaxDiscount() != null) {
            existing.setMaxDiscount(updated.getMaxDiscount());
        }
        if (updated.getStartDate() != null) {
            existing.setStartDate(updated.getStartDate());
        }
        if (updated.getExpiryDate() != null) {
            existing.setExpiryDate(updated.getExpiryDate());
        }
        if (updated.getUsageLimit() != null) {
            existing.setUsageLimit(updated.getUsageLimit());
        }
        if (updated.getUsagePerCustomer() != null) {
            existing.setUsagePerCustomer(updated.getUsagePerCustomer());
        }
        if (updated.getApplicableCategory() != null) {
            existing.setApplicableCategory(updated.getApplicableCategory());
        }
        if (updated.getIsActive() != null) {
            existing.setIsActive(updated.getIsActive());
        }

        Coupon saved = couponRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.success(saved, "Coupon updated successfully"));
    }

    @PutMapping("/admin/coupons/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Coupon>> toggleCouponActive(@PathVariable Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Coupon", id));
        coupon.setIsActive(!coupon.getIsActive());
        Coupon saved = couponRepository.save(coupon);
        return ResponseEntity.ok(ApiResponse.success(saved, "Coupon status toggled"));
    }

    @DeleteMapping("/admin/coupons/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Coupon", id));
        couponRepository.delete(coupon);
        return ResponseEntity.ok(ApiResponse.success(null, "Coupon deleted successfully"));
    }

    // -----------------------------------------------------------------------------
    // CUSTOMER ENDPOINTS
    // -----------------------------------------------------------------------------

    @GetMapping("/coupons/validate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateCoupon(
            HttpServletRequest request,
            @RequestParam String code,
            @RequestParam BigDecimal bidAmount,
            @RequestParam String category) {

        Long userId = authService.extractUserIdFromToken(request);
        if (userId == null) {
            throw new BadRequestException("Unauthorized access");
        }

        Coupon coupon = couponRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BadRequestException("Invalid coupon code."));

        if (Boolean.FALSE.equals(coupon.getIsActive())) {
            throw new BadRequestException("This coupon code is inactive.");
        }
        if (coupon.isExpired()) {
            throw new BadRequestException("This coupon has expired.");
        }
        if (bidAmount.compareTo(coupon.getMinBookingAmount()) < 0) {
            throw new BadRequestException("Minimum booking amount of Rs. " + coupon.getMinBookingAmount() + " is required.");
        }
        if (coupon.getApplicableCategory() != null && !coupon.getApplicableCategory().isBlank()
                && !coupon.getApplicableCategory().equalsIgnoreCase("ALL")
                && !coupon.getApplicableCategory().equalsIgnoreCase(category)) {
            throw new BadRequestException("This coupon is not applicable to " + category + " services.");
        }

        // Check global usage limit
        if (coupon.getUsageLimit() > 0) {
            long totalUsage = couponUsageRepository.countByCouponId(coupon.getId());
            if (totalUsage >= coupon.getUsageLimit()) {
                throw new BadRequestException("Coupon limit has been fully reached.");
            }
        }
        // Check usage per customer
        if (coupon.getUsagePerCustomer() > 0) {
            long customerUsage = couponUsageRepository.countByCouponIdAndUserId(coupon.getId(), userId);
            if (customerUsage >= coupon.getUsagePerCustomer()) {
                throw new BadRequestException("You have already reached the usage limit for this coupon.");
            }
        }

        // Calculate discount
        BigDecimal discount = BigDecimal.ZERO;
        if ("FLAT".equalsIgnoreCase(coupon.getDiscountType())) {
            discount = coupon.getDiscountValue();
        } else if ("PERCENTAGE".equalsIgnoreCase(coupon.getDiscountType())) {
            BigDecimal pct = coupon.getDiscountValue().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            discount = bidAmount.multiply(pct).setScale(2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null && coupon.getMaxDiscount().compareTo(BigDecimal.ZERO) > 0) {
                discount = discount.min(coupon.getMaxDiscount());
            }
        }
        discount = discount.min(bidAmount);
        BigDecimal finalPayable = bidAmount.subtract(discount).max(BigDecimal.ZERO);

        Map<String, Object> result = new HashMap<>();
        result.put("couponCode", coupon.getCode());
        result.put("discountAmount", discount);
        result.put("payableAmount", finalPayable);
        result.put("isValid", true);

        return ResponseEntity.ok(ApiResponse.success(result, "Coupon applied successfully."));
    }
}

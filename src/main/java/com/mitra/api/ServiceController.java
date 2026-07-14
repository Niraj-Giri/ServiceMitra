package com.mitra.api;

import com.mitra.common.ApiResponse;
import com.mitra.services.ServiceListing;
import com.mitra.services.ServiceListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public service listings API. No authentication required.
 */
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
// SEC-05: @CrossOrigin removed - CORS is centrally managed in SecurityConfig
public class ServiceController {

    private final ServiceListingRepository serviceListingRepository;

    /**
     * GET /api/v1/services
     * Returns all active services. Optional ?category= filter.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceListing>>> getServices(
            @RequestParam(required = false) String category) {
        List<ServiceListing> services = category != null && !category.isBlank()
                ? serviceListingRepository.findByCategoryAndIsActiveTrueOrderByNameAsc(category)
                : serviceListingRepository.findByIsActiveTrueOrderByNameAsc();
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    /**
     * GET /api/v1/services/{id}
     * Returns a single service by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceListing>> getServiceById(@PathVariable Long id) {
        return serviceListingRepository.findByIdAndIsActiveTrue(id)
                .map(s -> ResponseEntity.ok(ApiResponse.success(s)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

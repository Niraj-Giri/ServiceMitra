package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.common.ApiResponse;
import com.mitra.common.ResourceNotFoundException;
import com.mitra.common.ForbiddenException;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import com.mitra.users.UserAddress;
import com.mitra.users.UserAddressRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users/addresses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Transactional(readOnly = true)
// SEC-05: @CrossOrigin removed - CORS is centrally managed in SecurityConfig
public class UserAddressController {

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final AuthService authService;

    @Data
    @Builder
    public static class AddressDto {
        private Long id;
        private String label;
        private String line1;
        private Double latitude;
        private Double longitude;
        private Boolean isDefault;
    }

    @Data
    public static class CreateAddressRequest {
        private String label;
        private String line1;
        private Double latitude;
        private Double longitude;
        private Boolean isDefault;
    }

    /**
     * GET /api/v1/users/addresses
     * Returns list of addresses for currently authenticated user.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressDto>>> getAddresses(HttpServletRequest request) {
        Long userId = authService.extractUserIdFromToken(request);
        List<UserAddress> addresses = userAddressRepository.findByUserId(userId);
        
        List<AddressDto> dtos = addresses.stream()
                .map(addr -> AddressDto.builder()
                        .id(addr.getId())
                        .label(addr.getLabel())
                        .line1(addr.getLine1())
                        .latitude(addr.getLatitude())
                        .longitude(addr.getLongitude())
                        .isDefault(addr.getIsDefault())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    /**
     * POST /api/v1/users/addresses
     * Adds a new address to the user's account.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<AddressDto>> addAddress(
            HttpServletRequest request,
            @RequestBody CreateAddressRequest body) {
        
        Long userId = authService.extractUserIdFromToken(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        // If isDefault is true, unset default on other addresses
        if (Boolean.TRUE.equals(body.getIsDefault())) {
            List<UserAddress> existing = userAddressRepository.findByUserId(userId);
            for (UserAddress addr : existing) {
                if (Boolean.TRUE.equals(addr.getIsDefault())) {
                    addr.setIsDefault(false);
                    userAddressRepository.save(addr);
                }
            }
        }

        UserAddress newAddress = UserAddress.builder()
                .user(user)
                .label(body.getLabel() == null ? "Home" : body.getLabel())
                .line1(body.getLine1())
                .latitude(body.getLatitude())
                .longitude(body.getLongitude())
                .isDefault(body.getIsDefault() != null && body.getIsDefault())
                .build();

        UserAddress saved = userAddressRepository.save(newAddress);

        AddressDto dto = AddressDto.builder()
                .id(saved.getId())
                .label(saved.getLabel())
                .line1(saved.getLine1())
                .latitude(saved.getLatitude())
                .longitude(saved.getLongitude())
                .isDefault(saved.getIsDefault())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * DELETE /api/v1/users/addresses/{id}
     * Deletes a saved address.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<String>> deleteAddress(
            HttpServletRequest request,
            @PathVariable Long id) {
        
        Long userId = authService.extractUserIdFromToken(request);
        UserAddress address = userAddressRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Address", id));

        if (!address.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You cannot delete an address belonging to another account.");
        }

        userAddressRepository.delete(address);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully."));
    }
}

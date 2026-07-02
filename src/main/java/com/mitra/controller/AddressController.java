package com.mitra.controller;

import com.mitra.entity.UserAddress;
import com.mitra.entity.UserAddressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/addresses")
@CrossOrigin(origins = "*")
public class AddressController {

    @Autowired
    private UserAddressRepository userAddressRepository;

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<UserAddress>> getCustomerAddresses(@PathVariable Long customerId) {
        return ResponseEntity.ok(userAddressRepository.findByCustomerId(customerId));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createAddress(@RequestBody UserAddress userAddress) {
        // If it's the first address, we might want to make it default, or respect what's in the request.
        // The problem statement says: POST /api/addresses/create - body: UserAddress
        UserAddress saved = userAddressRepository.save(userAddress);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<?> setDefaultAddress(@PathVariable Long id) {
        Optional<UserAddress> opt = userAddressRepository.findById(id);
        if (opt.isPresent()) {
            UserAddress address = opt.get();
            Long customerId = address.getCustomerId();
            
            // Unset previous defaults
            List<UserAddress> allAddresses = userAddressRepository.findByCustomerId(customerId);
            for (UserAddress a : allAddresses) {
                if (a.getIsDefault() != null && a.getIsDefault()) {
                    a.setIsDefault(false);
                    userAddressRepository.save(a);
                }
            }
            
            address.setIsDefault(true);
            userAddressRepository.save(address);
            return ResponseEntity.ok(Collections.singletonMap("status", "success"));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAddress(@PathVariable Long id) {
        userAddressRepository.deleteById(id);
        return ResponseEntity.ok(Collections.singletonMap("status", "success"));
    }
}

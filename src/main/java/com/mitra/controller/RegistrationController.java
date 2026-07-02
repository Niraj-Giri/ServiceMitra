package com.mitra.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import com.mitra.entity.Provider;
import com.mitra.entity.ProviderRepository;
import java.util.*;

@RestController
@RequestMapping("/api/providers")
@CrossOrigin(origins = "*")
public class RegistrationController {

    @Autowired
    private ProviderRepository providerRepository;

    @PostMapping("/register")
    public ResponseEntity<?> registerProvider(@RequestBody Map<String, String> request) {
        Provider provider = new Provider();
        provider.setFullName(request.get("name"));
        provider.setEmail(request.get("email"));
        provider.setPhone(request.get("phone"));
        provider.setPassword(request.get("password"));
        provider.setBusinessName(request.get("businessName"));
        provider.setServiceCategory(request.get("serviceCategory")); // e.g. AUTO_MECHANIC, AC_TECHNICIAN
        provider.setAddress(request.get("address"));
        provider.setPanFileUrl(request.get("panFileUrl"));
        provider.setCitizenFileUrl(request.get("citizenFileUrl"));
        if (request.containsKey("latitude") && request.get("latitude") != null) {
            provider.setLatitude(Double.parseDouble(request.get("latitude")));
        }
        if (request.containsKey("longitude") && request.get("longitude") != null) {
            provider.setLongitude(Double.parseDouble(request.get("longitude")));
        }
        
        providerRepository.save(provider);
                
        return ResponseEntity.ok(Collections.singletonMap("status", "registered"));
    }
}

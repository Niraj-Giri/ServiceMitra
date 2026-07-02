package com.mitra.provider;

import com.mitra.entity.ProviderProfile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderProfileService {
    
    private final ProviderProfileRepository repository;

    public ProviderProfileService(ProviderProfileRepository repository) {
        this.repository = repository;
    }

    public List<ProviderProfile> getNearbyMechanics(double lat, double lon, double radius) {
        String point = String.format("POINT(%f %f)", lon, lat);
        return repository.findNearbyProviders(point, radius);
    }
}

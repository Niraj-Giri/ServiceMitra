package com.mitra.provider;

import com.mitra.entity.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, Long> {

    @Query(value = "SELECT * FROM provider_profiles p " +
            "WHERE p.is_available = true " +
            "AND ST_Distance_Sphere(p.geo_location, ST_GeomFromText(:point, 4326)) <= :radius", 
            nativeQuery = true)
    List<ProviderProfile> findNearbyProviders(@Param("point") String point, @Param("radius") double radiusInMeters);
}

package com.mitra.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {
    List<UserAddress> findByCustomerId(Long customerId);
    Optional<UserAddress> findByCustomerIdAndIsDefaultTrue(Long customerId);
}

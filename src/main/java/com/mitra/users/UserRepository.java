package com.mitra.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    java.util.List<User> findByIsDeletedFalse();
    Optional<User> findByPhoneAndIsDeletedFalse(String phone);
    
    @org.springframework.data.jpa.repository.Query("""
        SELECT u FROM User u
        WHERE u.isDeleted = FALSE
          AND (:search IS NULL OR :search = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(u.phone) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    org.springframework.data.domain.Page<User> findCustomers(
        @org.springframework.data.repository.query.Param("search") String search,
        org.springframework.data.domain.Pageable pageable
    );
}

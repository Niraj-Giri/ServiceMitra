package com.mitra.taskrequests;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRequestRepository extends JpaRepository<TaskRequest, Long> {

    /** Customer's own tasks, newest first. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    List<TaskRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find OPEN or QUOTING tasks for a provider — matching by service category
     * and within a radius using the Haversine formula.
     *
     * Radius is in kilometres. The Haversine formula result is in km.
     *
     * @param category   Provider's service category (exact match)
     * @param lat        Provider's latitude
     * @param lng        Provider's longitude
     * @param radiusKm   Search radius in kilometres
     * @param providerId Exclude tasks where this provider already has a quote
     */
    @Query(value = """
        SELECT tr.* FROM task_requests tr
        WHERE tr.category = :category
          AND tr.status IN ('OPEN', 'QUOTING')
          AND (
            tr.latitude IS NULL OR tr.longitude IS NULL OR
            (
              6371 * ACOS(
                LEAST(1.0, GREATEST(-1.0, 
                  COS(RADIANS(:lat)) * COS(RADIANS(tr.latitude)) *
                  COS(RADIANS(tr.longitude) - RADIANS(:lng)) +
                  SIN(RADIANS(:lat)) * SIN(RADIANS(tr.latitude))
                ))
              )
            ) <= :radiusKm
          )
          AND tr.id NOT IN (
            SELECT q.task_request_id FROM quotes q
            WHERE q.provider_id = :providerId
              AND q.status NOT IN ('WITHDRAWN', 'REJECTED', 'EXPIRED')
          )
        ORDER BY tr.created_at DESC
        """, nativeQuery = true)
    List<TaskRequest> findOpenTasksNearProvider(
        @Param("category")   String category,
        @Param("lat")        Double lat,
        @Param("lng")        Double lng,
        @Param("radiusKm")   Double radiusKm,
        @Param("providerId") Long providerId
    );

    /** Admin — list tasks by status. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    List<TaskRequest> findByStatusOrderByCreatedAtDesc(TaskRequestStatus status);

    /** Admin — all tasks newest first. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    List<TaskRequest> findAllByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    @Query("SELECT tr FROM TaskRequest tr JOIN Quote q ON tr.acceptedQuoteId = q.id WHERE q.provider.id = :providerId ORDER BY tr.createdAt DESC")
    List<TaskRequest> findAssignedTasksForProvider(@Param("providerId") Long providerId);

    long countByStatus(TaskRequestStatus status);

    @Query("SELECT COUNT(tr) FROM TaskRequest tr WHERE tr.createdAt >= :startOfDay")
    long countTasksCreatedAfter(@Param("startOfDay") java.time.LocalDateTime startOfDay);

    @Query("""
        SELECT tr FROM TaskRequest tr
        WHERE (:status IS NULL OR tr.status = :status)
          AND (:search IS NULL OR :search = '' OR CAST(tr.id AS string) LIKE CONCAT('%', :search, '%')
                             OR LOWER(tr.user.name) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(tr.title) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(tr.serviceName) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    org.springframework.data.domain.Page<TaskRequest> findTasks(
        @Param("status") TaskRequestStatus status,
        @Param("search") String search,
        org.springframework.data.domain.Pageable pageable
    );

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    org.springframework.data.domain.Page<TaskRequest> findByUserIdOrderByCreatedAtDesc(Long userId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT tr FROM TaskRequest tr JOIN Quote q ON tr.acceptedQuoteId = q.id WHERE q.provider.id = :providerId ORDER BY tr.createdAt DESC")
    org.springframework.data.domain.Page<TaskRequest> findAssignedTasksForProviderPaged(@Param("providerId") Long providerId, org.springframework.data.domain.Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, TaskRequestStatus status);

    @Query("SELECT COUNT(tr) FROM TaskRequest tr JOIN Quote q ON tr.acceptedQuoteId = q.id WHERE q.provider.id = :providerId AND tr.status = :status")
    long countAssignedByProviderAndStatus(@Param("providerId") Long providerId, @Param("status") TaskRequestStatus status);

    @Query("SELECT COUNT(tr) FROM TaskRequest tr JOIN Quote q ON tr.acceptedQuoteId = q.id WHERE q.provider.id = :providerId")
    long countAssignedByProvider(@Param("providerId") Long providerId);
}

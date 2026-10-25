package com.trustvault.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {

    @Query("SELECT d FROM Document d WHERE d.owner.id = :userId " +
           "OR d.id IN (SELECT p.document.id FROM DocumentPermission p WHERE p.user.id = :userId AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP))")
    Page<Document> findAccessibleDocuments(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE (d.owner.id = :userId " +
           "OR d.id IN (SELECT p.document.id FROM DocumentPermission p WHERE p.user.id = :userId AND (p.expiresAt IS NULL OR p.expiresAt > CURRENT_TIMESTAMP))) " +
           "AND (:title IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', :title, '%'))) " +
           "AND (:categoryId IS NULL OR d.category.id = :categoryId) " +
           "AND (:ownerId IS NULL OR d.owner.id = :ownerId) " +
           "AND (:fromDate IS NULL OR d.createdAt >= :fromDate) " +
           "AND (:toDate IS NULL OR d.createdAt <= :toDate)")
    Page<Document> searchAccessibleDocuments(
            @Param("userId") String userId,
            @Param("title") String title,
            @Param("categoryId") String categoryId,
            @Param("ownerId") String ownerId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );

    Page<Document> findByOwnerId(String ownerId, Pageable pageable);

    long countByOwnerId(String ownerId);

    Optional<Document> findByStorageKey(String storageKey);
}

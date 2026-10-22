package com.trustvault.permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, String> {

    List<DocumentPermission> findByDocumentId(String documentId);

    Optional<DocumentPermission> findByDocumentIdAndUserId(String documentId, String userId);

    boolean existsByDocumentIdAndUserId(String documentId, String userId);

    void deleteByDocumentIdAndUserId(String documentId, String userId);

    List<DocumentPermission> findByUserId(String userId);

    long countByUserId(String userId);
}

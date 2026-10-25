package com.trustvault.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    Optional<AuditEvent> findTopByOrderBySequenceNumberDesc();

    List<AuditEvent> findAllByOrderBySequenceNumberAsc();

    Page<AuditEvent> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AuditEvent> findByOutcomeOrderByTimestampDesc(AuditOutcome outcome, Pageable pageable);

    Page<AuditEvent> findByActionOrderByTimestampDesc(AuditEventType action, Pageable pageable);

    Page<AuditEvent> findByTargetIdOrderByTimestampDesc(String targetId, Pageable pageable);

    Page<AuditEvent> findByActorIdOrderByTimestampDesc(String actorId, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:outcome IS NULL OR a.outcome = :outcome) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:user IS NULL OR :user = '' OR LOWER(a.actorUsername) LIKE LOWER(CONCAT('%', :user, '%')) OR a.actorId = :user) AND " +
           "(:document IS NULL OR :document = '' OR a.targetId = :document OR LOWER(a.details) LIKE LOWER(CONCAT('%', :document, '%'))) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate)")
    Page<AuditEvent> findFilteredEvents(
            @Param("outcome") AuditOutcome outcome,
            @Param("action") AuditEventType action,
            @Param("user") String user,
            @Param("document") String document,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    long countByOutcome(AuditOutcome outcome);

    long countByAction(AuditEventType action);

    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.action = :action AND a.outcome = :outcome")
    long countByActionAndOutcome(@Param("action") AuditEventType action, @Param("outcome") AuditOutcome outcome);

    Optional<AuditEvent> findTopByActionAndOutcomeOrderByTimestampDesc(AuditEventType action, AuditOutcome outcome);

    List<AuditEvent> findTop10ByOrderByTimestampDesc();
}

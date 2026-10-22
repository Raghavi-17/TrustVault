package com.trustvault.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    long countByOutcome(AuditOutcome outcome);

    long countByAction(AuditEventType action);

    @Query("SELECT COUNT(a) FROM AuditEvent a WHERE a.action = :action AND a.outcome = :outcome")
    long countByActionAndOutcome(@Param("action") AuditEventType action, @Param("outcome") AuditOutcome outcome);

    List<AuditEvent> findTop10ByOrderByTimestampDesc();
}

package com.society.complaint.repository;

import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.User;
import com.society.complaint.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    /**
     * Fetches a single complaint together with resident, worker, and statusHistory
     * in one query. This is what every "view complaint detail" page should use,
     * since open-in-view is disabled (application.properties) and the Thymeleaf
     * templates access complaint.resident, complaint.worker, and
     * complaint.statusHistory AFTER the controller method has returned —
     * without this fetch join, that access would throw LazyInitializationException.
     */
    @Query("SELECT c FROM Complaint c " +
           "LEFT JOIN FETCH c.resident " +
           "LEFT JOIN FETCH c.worker " +
           "LEFT JOIN FETCH c.statusHistory " +
           "WHERE c.id = :id")
    Optional<Complaint> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT DISTINCT c FROM Complaint c " +
           "LEFT JOIN FETCH c.resident " +
           "LEFT JOIN FETCH c.worker " +
           "WHERE c.resident = :resident " +
           "ORDER BY c.createdAt DESC")
    List<Complaint> findByResidentOrderByCreatedAtDesc(@Param("resident") User resident);

    @Query("SELECT DISTINCT c FROM Complaint c " +
           "LEFT JOIN FETCH c.resident " +
           "LEFT JOIN FETCH c.worker " +
           "WHERE c.worker = :worker " +
           "ORDER BY c.createdAt DESC")
    List<Complaint> findByWorkerOrderByCreatedAtDesc(@Param("worker") User worker);

    @Query("SELECT DISTINCT c FROM Complaint c " +
           "LEFT JOIN FETCH c.resident " +
           "LEFT JOIN FETCH c.worker " +
           "ORDER BY c.createdAt DESC")
    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);

    @Query("SELECT DISTINCT c FROM Complaint c " +
           "LEFT JOIN FETCH c.resident " +
           "LEFT JOIN FETCH c.worker " +
           "WHERE c.overdue = true " +
           "ORDER BY c.slaDeadline ASC")
    List<Complaint> findByOverdueTrueOrderBySlaDeadlineAsc();

    // Used by the scheduler: find complaints that are past their SLA deadline,
    // are not yet flagged overdue, and are not already resolved/closed.
    @Query("SELECT c FROM Complaint c WHERE c.slaDeadline < :now " +
           "AND c.overdue = false " +
           "AND c.status NOT IN ('RESOLVED', 'CLOSED')")
    List<Complaint> findNewlyOverdueComplaints(@Param("now") LocalDateTime now);

    long countByStatus(ComplaintStatus status);

    long countByOverdueTrue();
}

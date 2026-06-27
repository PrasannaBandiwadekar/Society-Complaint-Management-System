package com.society.complaint.service.impl;

import com.society.complaint.dto.ComplaintDto;
import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.ComplaintStatusHistory;
import com.society.complaint.entity.User;
import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.repository.ComplaintRepository;
import com.society.complaint.repository.UserRepository;
import com.society.complaint.service.ComplaintService;
import com.society.complaint.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public Complaint createComplaint(ComplaintDto dto, User resident) {
        Complaint complaint = Complaint.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .category(dto.getCategory())
                .priority(dto.getPriority())
                .status(ComplaintStatus.OPEN)
                .resident(resident)
                .flatNumber(dto.getFlatNumber() != null && !dto.getFlatNumber().isBlank()
                        ? dto.getFlatNumber() : resident.getFlatNumber())
                .build();

        Complaint saved = complaintRepository.save(complaint);

        ComplaintStatusHistory history = ComplaintStatusHistory.builder()
                .oldStatus(ComplaintStatus.OPEN)
                .newStatus(ComplaintStatus.OPEN)
                .changedBy(resident.getEmail())
                .remarks("Complaint raised by resident")
                .build();
        saved.addStatusHistory(history);
        complaintRepository.save(saved);

        emailService.sendComplaintCreatedEmail(saved);
        return saved;
    }

    @Override
    public Complaint getById(Long id) {
        // Uses the fetch-joined query (resident + worker + statusHistory) because
        // this method backs every "view complaint detail" page, and open-in-view
        // is disabled - the Hibernate session is closed before Thymeleaf renders,
        // so any lazy field touched in the template would otherwise throw
        // LazyInitializationException.
        return complaintRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with id: " + id));
    }

    @Override
    public List<Complaint> getComplaintsForResident(User resident) {
        return complaintRepository.findByResidentOrderByCreatedAtDesc(resident);
    }

    @Override
    public List<Complaint> getComplaintsForWorker(User worker) {
        return complaintRepository.findByWorkerOrderByCreatedAtDesc(worker);
    }

    @Override
    public List<Complaint> getAllComplaints() {
        return complaintRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<Complaint> getOverdueComplaints() {
        return complaintRepository.findByOverdueTrueOrderBySlaDeadlineAsc();
    }

    @Override
    @Transactional
    public Complaint assignWorker(Long complaintId, Long workerId, String changedByEmail) {
        Complaint complaint = getById(complaintId);
        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> new IllegalArgumentException("Worker not found with id: " + workerId));

        ComplaintStatus oldStatus = complaint.getStatus();

        complaint.setWorker(worker);
        complaint.setStatus(ComplaintStatus.ASSIGNED);
        complaint.setAssignedAt(LocalDateTime.now());

        ComplaintStatusHistory history = ComplaintStatusHistory.builder()
                .oldStatus(oldStatus)
                .newStatus(ComplaintStatus.ASSIGNED)
                .changedBy(changedByEmail)
                .remarks("Assigned to " + worker.getFullName())
                .build();
        complaint.addStatusHistory(history);

        Complaint saved = complaintRepository.save(complaint);
        emailService.sendComplaintAssignedEmail(saved);
        return saved;
    }

    @Override
    @Transactional
    public Complaint updateStatus(Long complaintId, ComplaintStatus newStatus, String remarks, String changedByEmail) {
        Complaint complaint = getById(complaintId);
        ComplaintStatus oldStatus = complaint.getStatus();

        complaint.setStatus(newStatus);

        if (newStatus == ComplaintStatus.RESOLVED || newStatus == ComplaintStatus.CLOSED) {
            complaint.setResolvedAt(LocalDateTime.now());
            // Resolving clears the overdue flag - it's no longer breaching SLA going forward
            complaint.setOverdue(false);
        }

        // If a resident reopens a resolved complaint, treat it as active again
        if (newStatus == ComplaintStatus.REOPENED) {
            complaint.setResolvedAt(null);
        }

        ComplaintStatusHistory history = ComplaintStatusHistory.builder()
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(changedByEmail)
                .remarks(remarks)
                .build();
        complaint.addStatusHistory(history);

        Complaint saved = complaintRepository.save(complaint);
        emailService.sendStatusChangedEmail(saved, oldStatus.name(), newStatus.name());
        return saved;
    }

    /**
     * Core SLA monitoring logic, invoked by the scheduled job.
     * Finds complaints whose SLA deadline has passed and are not yet
     * resolved/closed, flags them overdue, and fires an escalation email
     * to all admins (only once per complaint, via the escalationSent flag).
     */
    @Override
    @Transactional
    public void runSlaCheck() {
        LocalDateTime now = LocalDateTime.now();
        List<Complaint> newlyOverdue = complaintRepository.findNewlyOverdueComplaints(now);

        if (newlyOverdue.isEmpty()) {
            log.info("SLA check ran at {} - no newly overdue complaints found.", now);
            return;
        }

        for (Complaint complaint : newlyOverdue) {
            complaint.setOverdue(true);
            if (!complaint.isEscalationSent()) {
                complaint.setEscalationSent(true);
                emailService.sendOverdueEscalationEmail(complaint);
            }
            complaintRepository.save(complaint);
            log.warn("Complaint #{} flagged OVERDUE (deadline was {})", complaint.getId(), complaint.getSlaDeadline());
        }
    }

    @Override
    public long countByStatus(ComplaintStatus status) {
        return complaintRepository.countByStatus(status);
    }

    @Override
    public long countOverdue() {
        return complaintRepository.countByOverdueTrue();
    }
}

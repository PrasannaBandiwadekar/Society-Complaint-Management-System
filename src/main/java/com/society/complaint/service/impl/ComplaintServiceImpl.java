package com.society.complaint.service.impl;

import com.society.complaint.dto.ComplaintDto;
import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.ComplaintStatusHistory;
import com.society.complaint.entity.User;
import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.enums.Role;
import com.society.complaint.kafka.ComplaintEvent;
import com.society.complaint.kafka.ComplaintEventProducer;
import com.society.complaint.repository.ComplaintRepository;
import com.society.complaint.repository.UserRepository;
import com.society.complaint.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final ComplaintEventProducer eventProducer; // Kafka producer replaces EmailService

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

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

        // Publish to Kafka — the EmailNotificationConsumer picks this up
        // and sends the email asynchronously, decoupled from this transaction
        eventProducer.publish(ComplaintEvent.builder()
                .eventType(ComplaintEvent.EventType.COMPLAINT_CREATED)
                .complaintId(saved.getId())
                .complaintTitle(saved.getTitle())
                .complaintDescription(saved.getDescription())
                .category(saved.getCategory().name())
                .priority(saved.getPriority().name())
                .status(saved.getStatus().name())
                .flatNumber(saved.getFlatNumber())
                .slaDeadline(saved.getSlaDeadline().format(FORMATTER))
                .residentName(resident.getFullName())
                .residentEmail(resident.getEmail())
                .build());

        return saved;
    }

    @Override
    public Complaint getById(Long id) {
        return complaintRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Complaint not found with id: " + id));
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
                .orElseThrow(() -> new IllegalArgumentException(
                        "Worker not found with id: " + workerId));

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

        // Publish ASSIGNED event to Kafka
        eventProducer.publish(ComplaintEvent.builder()
                .eventType(ComplaintEvent.EventType.COMPLAINT_ASSIGNED)
                .complaintId(saved.getId())
                .complaintTitle(saved.getTitle())
                .complaintDescription(saved.getDescription())
                .category(saved.getCategory().name())
                .priority(saved.getPriority().name())
                .status(saved.getStatus().name())
                .flatNumber(saved.getFlatNumber())
                .slaDeadline(saved.getSlaDeadline().format(FORMATTER))
                .residentName(saved.getResident().getFullName())
                .residentEmail(saved.getResident().getEmail())
                .workerName(worker.getFullName())
                .workerEmail(worker.getEmail())
                .workerSpecialization(worker.getSpecialization())
                .build());

        return saved;
    }

    @Override
    @Transactional
    public Complaint updateStatus(Long complaintId, ComplaintStatus newStatus,
                                  String remarks, String changedByEmail) {
        Complaint complaint = getById(complaintId);
        ComplaintStatus oldStatus = complaint.getStatus();

        complaint.setStatus(newStatus);

        if (newStatus == ComplaintStatus.RESOLVED || newStatus == ComplaintStatus.CLOSED) {
            complaint.setResolvedAt(LocalDateTime.now());
            complaint.setOverdue(false);
        }
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

        // Publish STATUS_CHANGED event to Kafka
        eventProducer.publish(ComplaintEvent.builder()
                .eventType(ComplaintEvent.EventType.STATUS_CHANGED)
                .complaintId(saved.getId())
                .complaintTitle(saved.getTitle())
                .status(newStatus.name())
                .oldStatus(oldStatus.name())
                .newStatus(newStatus.name())
                .residentName(saved.getResident().getFullName())
                .residentEmail(saved.getResident().getEmail())
                .build());

        return saved;
    }

    @Override
    @Transactional
    public void runSlaCheck() {
        LocalDateTime now = LocalDateTime.now();
        List<Complaint> newlyOverdue =
                complaintRepository.findNewlyOverdueComplaints(now);

        if (newlyOverdue.isEmpty()) {
            log.info("SLA check ran at {} - no newly overdue complaints.", now);
            return;
        }

        // Fetch all admin emails once, not per complaint
        List<String> adminEmails = userRepository.findByRole(Role.ADMIN)
                .stream()
                .map(User::getEmail)
                .collect(Collectors.toList());

        for (Complaint complaint : newlyOverdue) {
            complaint.setOverdue(true);
            if (!complaint.isEscalationSent()) {
                complaint.setEscalationSent(true);

                // Publish OVERDUE event to Kafka
                eventProducer.publish(ComplaintEvent.builder()
                        .eventType(ComplaintEvent.EventType.COMPLAINT_OVERDUE)
                        .complaintId(complaint.getId())
                        .complaintTitle(complaint.getTitle())
                        .status(complaint.getStatus().name())
                        .priority(complaint.getPriority().name())
                        .flatNumber(complaint.getFlatNumber())
                        .slaDeadline(complaint.getSlaDeadline().format(FORMATTER))
                        .residentName(complaint.getResident().getFullName())
                        .workerName(complaint.getWorker() != null
                                ? complaint.getWorker().getFullName() : "Not assigned")
                        .adminEmails(adminEmails)
                        .build());
            }
            complaintRepository.save(complaint);
            log.warn("Complaint #{} flagged OVERDUE", complaint.getId());
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

package com.society.complaint.api;

import com.society.complaint.config.CustomUserDetails;
import com.society.complaint.dto.ComplaintDto;
import com.society.complaint.entity.Complaint;
import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for complaints — returns JSON, not HTML.
 *
 * All endpoints under /api/** require a valid JWT in the Authorization header.
 * Role-based access is enforced with @PreAuthorize, same as the Thymeleaf layer.
 *
 * Example requests (after getting a token from /api/auth/login):
 *
 *   GET  /api/complaints          (ADMIN only — all complaints)
 *   GET  /api/complaints/mine     (RESIDENT — their own complaints)
 *   POST /api/complaints          (RESIDENT — raise a complaint)
 *   PUT  /api/complaints/{id}/status  (ADMIN or WORKER — update status)
 */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintApiController {

    private final ComplaintService complaintService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    // ADMIN: get all complaints
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ComplaintResponse>> getAllComplaints() {
        return ResponseEntity.ok(
                complaintService.getAllComplaints().stream()
                        .map(ComplaintResponse::from)
                        .collect(Collectors.toList())
        );
    }

    // RESIDENT: get their own complaints
    @GetMapping("/mine")
    @PreAuthorize("hasRole('RESIDENT')")
    public ResponseEntity<List<ComplaintResponse>> getMyComplaints(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(
                complaintService.getComplaintsForResident(principal.getUser())
                        .stream()
                        .map(ComplaintResponse::from)
                        .collect(Collectors.toList())
        );
    }

    // WORKER: get their assigned complaints
    @GetMapping("/assigned")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<List<ComplaintResponse>> getAssignedComplaints(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(
                complaintService.getComplaintsForWorker(principal.getUser())
                        .stream()
                        .map(ComplaintResponse::from)
                        .collect(Collectors.toList())
        );
    }

    // ADMIN: get overdue complaints
    @GetMapping("/overdue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ComplaintResponse>> getOverdueComplaints() {
        return ResponseEntity.ok(
                complaintService.getOverdueComplaints().stream()
                        .map(ComplaintResponse::from)
                        .collect(Collectors.toList())
        );
    }

    // RESIDENT: raise a new complaint
    @PostMapping
    @PreAuthorize("hasRole('RESIDENT')")
    public ResponseEntity<ComplaintResponse> createComplaint(
            @Valid @RequestBody ComplaintDto dto,
            @AuthenticationPrincipal CustomUserDetails principal) {
        Complaint created = complaintService.createComplaint(dto, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ComplaintResponse.from(created));
    }

    // ADMIN / WORKER: update complaint status
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('WORKER')")
    public ResponseEntity<ComplaintResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        Complaint updated = complaintService.updateStatus(
                id,
                request.getStatus(),
                request.getRemarks(),
                principal.getUser().getEmail()
        );
        return ResponseEntity.ok(ComplaintResponse.from(updated));
    }

    // ADMIN: assign a worker to a complaint
    @PutMapping("/{id}/assign/{workerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComplaintResponse> assignWorker(
            @PathVariable Long id,
            @PathVariable Long workerId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        Complaint updated = complaintService.assignWorker(
                id, workerId, principal.getUser().getEmail());
        return ResponseEntity.ok(ComplaintResponse.from(updated));
    }

    // ---- Response / Request DTOs ----

    record ComplaintResponse(
            Long id, String title, String description,
            String category, String priority, String status,
            String flatNumber, String residentName,
            String workerName, String slaDeadline,
            boolean overdue, String createdAt
    ) {
        static ComplaintResponse from(Complaint c) {
            return new ComplaintResponse(
                    c.getId(), c.getTitle(), c.getDescription(),
                    c.getCategory().name(), c.getPriority().name(),
                    c.getStatus().name(), c.getFlatNumber(),
                    c.getResident() != null ? c.getResident().getFullName() : null,
                    c.getWorker() != null ? c.getWorker().getFullName() : null,
                    c.getSlaDeadline() != null ? c.getSlaDeadline().format(FMT) : null,
                    c.isOverdue(),
                    c.getCreatedAt() != null ? c.getCreatedAt().format(FMT) : null
            );
        }
    }

    @Data
    static class StatusUpdateRequest {
        private ComplaintStatus status;
        private String remarks;
    }
}

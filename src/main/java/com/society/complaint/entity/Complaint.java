package com.society.complaint.entity;

import com.society.complaint.enums.ComplaintCategory;
import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.enums.Priority;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;
/**
 * See the comment on User.java for why @Data is avoided here.
 * Excluding `resident`, `worker`, and `statusHistory` from toString() is essential:
 * Complaint -> User -> (User's own toString, fine) is safe now that User's @ToString
 * is limited, but excluding the collection also avoids triggering a lazy-load of
 * statusHistory just to log/print a Complaint object.
 */
@Entity
@Table(name = "complaints")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"resident", "worker", "statusHistory"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Complaint implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @NotBlank
    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    // The resident who raised the complaint
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private User resident;

    // The worker assigned to resolve it (nullable until admin assigns)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private User worker;

    private String flatNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime assignedAt;

    private LocalDateTime resolvedAt;

    // SLA deadline = createdAt + priority.getSlaHours(). Calculated once on creation.
    private LocalDateTime slaDeadline;

    @Column(nullable = false)
    private boolean overdue = false;

    // Set true once an overdue escalation email has been sent, to avoid spamming
    @Column(nullable = false)
    private boolean escalationSent = false;

    @OneToMany(mappedBy = "complaint", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ComplaintStatusHistory> statusHistory = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.priority != null) {
            this.slaDeadline = this.createdAt.plusHours(this.priority.getSlaHours());
        }
        if (this.status == null) {
            this.status = ComplaintStatus.OPEN;
        }
    }

    public void addStatusHistory(ComplaintStatusHistory history) {
        statusHistory.add(history);
        history.setComplaint(this);
    }
}

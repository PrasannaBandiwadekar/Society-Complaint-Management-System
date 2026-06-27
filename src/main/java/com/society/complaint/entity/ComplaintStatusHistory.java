package com.society.complaint.entity;

import com.society.complaint.enums.ComplaintStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Audit trail entity. Every time a complaint's status changes,
 * a new row is inserted here so the full history can be displayed
 * on the complaint detail page (who changed what, and when).
 *
 * `complaint` is excluded from toString() to break the circular reference
 * back to Complaint (which holds a List<ComplaintStatusHistory>).
 */
@Entity
@Table(name = "complaint_status_history")
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@ToString(exclude = "complaint")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplaintStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus newStatus;

    @Column(nullable = false)
    private String changedBy; // email of the user who made the change

    private String remarks;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        this.changedAt = LocalDateTime.now();
    }
}

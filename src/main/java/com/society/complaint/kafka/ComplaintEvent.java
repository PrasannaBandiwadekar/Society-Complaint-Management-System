package com.society.complaint.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The "message" that gets published to a Kafka topic whenever something
 * happens to a complaint (created, assigned, status changed, overdue).
 *
 * This is a plain Java object (POJO) — Spring Kafka serialises it to JSON
 * automatically before putting it on the topic, and deserialises it back
 * to this class when the consumer picks it up.
 *
 * Why a separate event class and not just pass the Complaint entity?
 * Because Kafka is a separate system — it shouldn't depend on your JPA
 * entities. This class carries only what the email consumer actually needs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplaintEvent {

    public enum EventType {
        COMPLAINT_CREATED,
        COMPLAINT_ASSIGNED,
        STATUS_CHANGED,
        COMPLAINT_OVERDUE
    }

    private EventType eventType;

    // Complaint info
    private Long complaintId;
    private String complaintTitle;
    private String complaintDescription;
    private String category;
    private String priority;
    private String status;
    private String flatNumber;
    private String slaDeadline;    // formatted as a string for easy email rendering

    // Resident info
    private String residentName;
    private String residentEmail;

    // Worker info (may be null if not yet assigned)
    private String workerName;
    private String workerEmail;
    private String workerSpecialization;

    // For STATUS_CHANGED events
    private String oldStatus;
    private String newStatus;

    // For COMPLAINT_OVERDUE — list of admin emails to notify
    private java.util.List<String> adminEmails;
}

package com.society.complaint.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * The CONSUMER — listens to the "complaint-notifications" Kafka topic and
 * sends the appropriate email for each event type it receives.
 *
 * Plain English analogy: this is the person at the post office who picks
 * up the letter, reads it, and decides what action to take.
 *
 * Key difference from the old @Async approach:
 * - Old: ComplaintServiceImpl directly called EmailService on a background thread.
 *        If the app restarted mid-send, the email was lost.
 * - New: ComplaintServiceImpl publishes to Kafka (fast, doesn't wait).
 *        This consumer picks up the event from Kafka whenever it's ready.
 *        If the app restarts, Kafka still holds the unprocessed event —
 *        the consumer will pick it up when the app comes back online.
 *
 * group-id = "email-group" means Kafka tracks which events this consumer
 * has already processed. If you add a second instance of the app, Kafka
 * will split events between them automatically (load balancing).
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationConsumer {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @KafkaListener(
            topics = ComplaintEventProducer.TOPIC,
            groupId = "email-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleComplaintEvent(ComplaintEvent event) {
        log.info("Received Kafka event: {} for complaint #{}",
                event.getEventType(), event.getComplaintId());

        try {
            switch (event.getEventType()) {
                case COMPLAINT_CREATED  -> handleCreated(event);
                case COMPLAINT_ASSIGNED -> handleAssigned(event);
                case STATUS_CHANGED     -> handleStatusChanged(event);
                case COMPLAINT_OVERDUE  -> handleOverdue(event);
            }
        } catch (Exception e) {
            // Log but don't rethrow — rethrowing would cause Kafka to retry
            // indefinitely, which could spam the user. Log and move on.
            log.error("Failed processing {} event for complaint #{}: {}",
                    event.getEventType(), event.getComplaintId(), e.getMessage(), e);
        }
    }

    private void handleCreated(ComplaintEvent event) {
        String subject = "Complaint Registered - #" + event.getComplaintId();
        String body = String.format(
                "Hi %s,%n%nYour complaint has been registered successfully.%n%n" +
                        "Complaint ID: #%d%nTitle: %s%nCategory: %s%nPriority: %s%nStatus: OPEN%n" +
                        "Expected resolution by: %s%n%n" +
                        "We will notify you when it is assigned to a technician.%n%n" +
                        "- Society Maintenance Team",
                event.getResidentName(), event.getComplaintId(),
                event.getComplaintTitle(), event.getCategory(),
                event.getPriority(), event.getSlaDeadline()
        );
        sendEmail(event.getResidentEmail(), subject, body);
    }

    private void handleAssigned(ComplaintEvent event) {
        // Email to resident
        String residentSubject = "Complaint #" + event.getComplaintId() + " Assigned to a Technician";
        String residentBody = String.format(
                "Hi %s,%n%nYour complaint \"%s\" has been assigned to %s (%s).%n" +
                        "Expected resolution by: %s%n%n- Society Maintenance Team",
                event.getResidentName(), event.getComplaintTitle(),
                event.getWorkerName(), event.getWorkerSpecialization(),
                event.getSlaDeadline()
        );
        sendEmail(event.getResidentEmail(), residentSubject, residentBody);

        // Email to worker
        String workerSubject = "New Complaint Assigned to You - #" + event.getComplaintId();
        String workerBody = String.format(
                "Hi %s,%n%nA complaint has been assigned to you.%n%n" +
                        "ID: #%d%nTitle: %s%nDescription: %s%nFlat: %s%nPriority: %s%n" +
                        "Please resolve before: %s%n%n- Society Maintenance Team",
                event.getWorkerName(), event.getComplaintId(),
                event.getComplaintTitle(), event.getComplaintDescription(),
                event.getFlatNumber(), event.getPriority(), event.getSlaDeadline()
        );
        sendEmail(event.getWorkerEmail(), workerSubject, workerBody);
    }

    private void handleStatusChanged(ComplaintEvent event) {
        String subject = "Complaint #" + event.getComplaintId()
                + " Status Updated: " + event.getNewStatus();
        String body = String.format(
                "Hi %s,%n%nYour complaint \"%s\" status has changed.%n%n" +
                        "Previous Status: %s%nNew Status: %s%n%n- Society Maintenance Team",
                event.getResidentName(), event.getComplaintTitle(),
                event.getOldStatus(), event.getNewStatus()
        );
        sendEmail(event.getResidentEmail(), subject, body);
    }

    private void handleOverdue(ComplaintEvent event) {
        String subject = "ESCALATION: Complaint #" + event.getComplaintId() + " is Overdue!";
        String body = String.format(
                "Alert,%n%nComplaint #%d (\"%s\") breached its SLA deadline of %s.%n" +
                        "Current Status: %s%nResident: %s%nFlat: %s%nPriority: %s%n%n" +
                        "Please take immediate action.%n%n- Automated SLA Monitor",
                event.getComplaintId(), event.getComplaintTitle(),
                event.getSlaDeadline(), event.getStatus(),
                event.getResidentName(), event.getFlatNumber(), event.getPriority()
        );
        // Send to all admins
        if (event.getAdminEmails() != null) {
            event.getAdminEmails().forEach(adminEmail ->
                    sendEmail(adminEmail, subject, body));
        }
    }

    private void sendEmail(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("[MAIL DISABLED] Would send to {} | Subject: {}", to, subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} | Subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}

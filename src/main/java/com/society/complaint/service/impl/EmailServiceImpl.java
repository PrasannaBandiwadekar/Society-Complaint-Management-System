package com.society.complaint.service.impl;

import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.User;
import com.society.complaint.repository.UserRepository;
import com.society.complaint.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;


@ConditionalOnBean(JavaMailSender.class)
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    @Override
    @Async
    public void sendComplaintCreatedEmail(Complaint complaint) {
        String subject = "Complaint Registered - #" + complaint.getId();
        String body = String.format(
                "Hi %s,%n%nYour complaint has been registered successfully.%n%n" +
                "Complaint ID: #%d%nTitle: %s%nCategory: %s%nPriority: %s%nStatus: %s%n" +
                "Expected resolution by: %s%n%n" +
                "We will notify you as soon as it is assigned and updated.%n%n" +
                "- Society Maintenance Team",
                complaint.getResident().getFullName(),
                complaint.getId(),
                complaint.getTitle(),
                complaint.getCategory(),
                complaint.getPriority(),
                complaint.getStatus(),
                complaint.getSlaDeadline().format(FORMATTER)
        );
        sendEmail(complaint.getResident().getEmail(), subject, body);
    }

    @Override
    @Async
    public void sendComplaintAssignedEmail(Complaint complaint) {
        // Notify resident
        String residentSubject = "Complaint #" + complaint.getId() + " Assigned to a Technician";
        String residentBody = String.format(
                "Hi %s,%n%nYour complaint \"%s\" has been assigned to %s (%s).%n" +
                "It is expected to be resolved by %s.%n%n- Society Maintenance Team",
                complaint.getResident().getFullName(),
                complaint.getTitle(),
                complaint.getWorker().getFullName(),
                complaint.getWorker().getSpecialization(),
                complaint.getSlaDeadline().format(FORMATTER)
        );
        sendEmail(complaint.getResident().getEmail(), residentSubject, residentBody);

        // Notify worker
        String workerSubject = "New Complaint Assigned - #" + complaint.getId();
        String workerBody = String.format(
                "Hi %s,%n%nA new complaint has been assigned to you.%n%n" +
                "Complaint ID: #%d%nTitle: %s%nDescription: %s%nFlat: %s%nPriority: %s%n" +
                "Please resolve before: %s%n%n- Society Maintenance Team",
                complaint.getWorker().getFullName(),
                complaint.getId(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getFlatNumber(),
                complaint.getPriority(),
                complaint.getSlaDeadline().format(FORMATTER)
        );
        sendEmail(complaint.getWorker().getEmail(), workerSubject, workerBody);
    }

    @Override
    @Async
    public void sendStatusChangedEmail(Complaint complaint, String oldStatus, String newStatus) {
        String subject = "Complaint #" + complaint.getId() + " Status Updated: " + newStatus;
        String body = String.format(
                "Hi %s,%n%nThe status of your complaint \"%s\" has changed.%n%n" +
                "Previous Status: %s%nNew Status: %s%n%n- Society Maintenance Team",
                complaint.getResident().getFullName(),
                complaint.getTitle(),
                oldStatus,
                newStatus
        );
        sendEmail(complaint.getResident().getEmail(), subject, body);
    }

    @Override
    @Async
    public void sendOverdueEscalationEmail(Complaint complaint) {
        String subject = "ESCALATION: Complaint #" + complaint.getId() + " is Overdue (SLA Breached)";
        String body = String.format(
                "Alert,%n%nComplaint #%d (\"%s\") has breached its SLA deadline of %s and is still marked as %s.%n%n" +
                "Resident: %s (%s)%nAssigned Worker: %s%nPriority: %s%n%n" +
                "Please take immediate action.%n%n- Automated SLA Monitor",
                complaint.getId(),
                complaint.getTitle(),
                complaint.getSlaDeadline().format(FORMATTER),
                complaint.getStatus(),
                complaint.getResident().getFullName(),
                complaint.getFlatNumber(),
                complaint.getWorker() != null ? complaint.getWorker().getFullName() : "Not yet assigned",
                complaint.getPriority()
        );

        // Send to every admin in the system
        List<User> admins = userRepository.findByRole(com.society.complaint.enums.Role.ADMIN);
        for (User admin : admins) {
            sendEmail(admin.getEmail(), subject, body);
        }
    }

    private void sendEmail(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("[MAIL DISABLED] Would have sent to {} | Subject: {}", to, subject);
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
            // Email failure should NEVER break the main business flow
            // (complaint creation / status update must still succeed).
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}

package com.society.complaint.service.impl;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.User;
import com.society.complaint.enums.Role;
import com.society.complaint.repository.UserRepository;
import com.society.complaint.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor   // <-- this is what was missing
@Slf4j
public class SendGridEmailService implements EmailService {

    private final UserRepository userRepository;

    // Defaults after the colon mean the app still starts when no key is set (local runs)
    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email:bandiwadekarprasanna@gmail.com}")
    private String fromEmail;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    @Override
    public void sendComplaintCreatedEmail(Complaint complaint) {
        String subject = "Complaint Registered - #" + complaint.getId();
        String body = String.format(
                "Hi %s,\n\nYour complaint has been registered.\n\n"
                        + "ID: #%d\nTitle: %s\nPriority: %s\nSLA Deadline: %s\n\n"
                        + "- Society Maintenance Team",
                complaint.getResident().getFullName(),
                complaint.getId(),
                complaint.getTitle(),
                complaint.getPriority(),
                complaint.getSlaDeadline().format(FORMATTER));
        sendEmail(complaint.getResident().getEmail(), subject, body);
    }

    @Override
    public void sendComplaintAssignedEmail(Complaint complaint) {
        String subject = "Complaint #" + complaint.getId() + " Assigned";
        String body = String.format(
                "Hi %s,\n\nYour complaint \"%s\" has been assigned to %s (%s).\n\n"
                        + "- Society Maintenance Team",
                complaint.getResident().getFullName(),
                complaint.getTitle(),
                complaint.getWorker().getFullName(),
                complaint.getWorker().getSpecialization());
        sendEmail(complaint.getResident().getEmail(), subject, body);

        String workerBody = String.format(
                "Hi %s,\n\nComplaint #%d has been assigned to you.\nTitle: %s\nFlat: %s\n\n"
                        + "- Society Maintenance Team",
                complaint.getWorker().getFullName(),
                complaint.getId(),
                complaint.getTitle(),
                complaint.getFlatNumber());
        sendEmail(complaint.getWorker().getEmail(),
                "New Assignment - #" + complaint.getId(), workerBody);
    }

    @Override
    public void sendStatusChangedEmail(Complaint complaint, String oldStatus, String newStatus) {
        String subject = "Complaint #" + complaint.getId() + " Status Updated: " + newStatus;
        String body = String.format(
                "Hi %s,\n\nComplaint \"%s\" status changed: %s -> %s\n\n"
                        + "- Society Maintenance Team",
                complaint.getResident().getFullName(),
                complaint.getTitle(), oldStatus, newStatus);
        sendEmail(complaint.getResident().getEmail(), subject, body);
    }

    @Override
    public void sendOverdueEscalationEmail(Complaint complaint) {
        String subject = "OVERDUE: Complaint #" + complaint.getId();
        String body = String.format(
                "Complaint #%d \"%s\" has breached its SLA deadline.\n"
                        + "Priority: %s | Status: %s\n\n- Automated SLA Monitor",
                complaint.getId(), complaint.getTitle(),
                complaint.getPriority(), complaint.getStatus());
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        for (User admin : admins) {
            sendEmail(admin.getEmail(), subject, body);
        }
    }

    private void sendEmail(String to, String subject, String body) {
        if (!mailEnabled || sendGridApiKey.isBlank()) {
            log.info("[EMAIL SKIPPED - mail disabled or no SendGrid key] To: {} | Subject: {}",
                    to, subject);
            return;
        }
        try {
            Email from = new Email(fromEmail);
            Email toEmail = new Email(to);
            Content content = new Content("text/plain", body);
            Mail mail = new Mail(from, subject, toEmail, content);

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("Email sent via SendGrid to {} | Subject: {}", to, subject);
            } else {
                log.error("SendGrid error: {} | Body: {}",
                        response.getStatusCode(), response.getBody());
            }
        } catch (IOException e) {
            log.error("Failed to send email via SendGrid to {}: {}", to, e.getMessage());
        }
    }
}
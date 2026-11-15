package com.society.complaint.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.User;
import com.society.complaint.enums.Role;
import com.society.complaint.repository.UserRepository;
import com.society.complaint.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailService implements EmailService {

    private final UserRepository userRepository;

    // Defaults after the colon: the app still starts when no key is configured
    @Value("${brevo.api.key:}")
    private String apiKey;

    @Value("${brevo.from.email:}")
    private String fromEmail;

    @Value("${brevo.from.name:Society Maintenance Desk}")
    private String fromName;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    private static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void sendComplaintCreatedEmail(Complaint c) {
        String body = String.format(
                "Hi %s,\n\nYour complaint has been registered.\n\n"
                        + "ID: #%d\nTitle: %s\nPriority: %s\nSLA Deadline: %s\n\n"
                        + "- Society Maintenance Team",
                c.getResident().getFullName(), c.getId(), c.getTitle(),
                c.getPriority(), c.getSlaDeadline().format(FORMATTER));
        sendEmail(c.getResident().getEmail(), "Complaint Registered - #" + c.getId(), body);
    }

    @Override
    public void sendComplaintAssignedEmail(Complaint c) {
        String residentBody = String.format(
                "Hi %s,\n\nYour complaint \"%s\" has been assigned to %s (%s).\n\n"
                        + "- Society Maintenance Team",
                c.getResident().getFullName(), c.getTitle(),
                c.getWorker().getFullName(), c.getWorker().getSpecialization());
        sendEmail(c.getResident().getEmail(), "Complaint #" + c.getId() + " Assigned", residentBody);

        String workerBody = String.format(
                "Hi %s,\n\nComplaint #%d has been assigned to you.\nTitle: %s\nFlat: %s\n\n"
                        + "- Society Maintenance Team",
                c.getWorker().getFullName(), c.getId(), c.getTitle(), c.getFlatNumber());
        sendEmail(c.getWorker().getEmail(), "New Assignment - #" + c.getId(), workerBody);
    }

    @Override
    public void sendStatusChangedEmail(Complaint c, String oldStatus, String newStatus) {
        String body = String.format(
                "Hi %s,\n\nComplaint \"%s\" status changed: %s -> %s\n\n"
                        + "- Society Maintenance Team",
                c.getResident().getFullName(), c.getTitle(), oldStatus, newStatus);
        sendEmail(c.getResident().getEmail(),
                "Complaint #" + c.getId() + " Status Updated: " + newStatus, body);
    }

    @Override
    public void sendOverdueEscalationEmail(Complaint c) {
        String body = String.format(
                "Complaint #%d \"%s\" has breached its SLA deadline.\n"
                        + "Priority: %s | Status: %s\n\n- Automated SLA Monitor",
                c.getId(), c.getTitle(), c.getPriority(), c.getStatus());
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        for (User admin : admins) {
            sendEmail(admin.getEmail(), "OVERDUE: Complaint #" + c.getId(), body);
        }
    }

    private void sendEmail(String to, String subject, String body) {
        if (!mailEnabled || apiKey.isBlank() || fromEmail.isBlank()) {
            log.info("[EMAIL SKIPPED - disabled or Brevo not configured] To: {} | Subject: {}",
                    to, subject);
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "sender", Map.of("name", fromName, "email", fromEmail),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "textContent", body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent via Brevo to {} | Subject: {}", to, subject);
            } else {
                log.error("Brevo error {} | Body: {}", response.statusCode(), response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Brevo send interrupted for {}", to);
        } catch (Exception e) {
            log.error("Failed to send email via Brevo to {}: {}", to, e.getMessage());
        }
    }
}
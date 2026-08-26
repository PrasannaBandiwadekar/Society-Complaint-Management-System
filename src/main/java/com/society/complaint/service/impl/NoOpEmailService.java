package com.society.complaint.service.impl;

import com.society.complaint.entity.Complaint;
import com.society.complaint.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(JavaMailSender.class)
@Slf4j
public class NoOpEmailService implements EmailService {

    @Override
    public void sendComplaintCreatedEmail(Complaint complaint) {
        log.info("[NO-OP EMAIL] Skipped complaint created email for #{}",
                complaint.getId());
    }

    @Override
    public void sendComplaintAssignedEmail(Complaint complaint) {
        log.info("[NO-OP EMAIL] Skipped assignment email for #{}",
                complaint.getId());
    }

    @Override
    public void sendStatusChangedEmail(Complaint complaint,
                                       String oldStatus, String newStatus) {
        log.info("[NO-OP EMAIL] Skipped status change email for #{}",
                complaint.getId());
    }

    @Override
    public void sendOverdueEscalationEmail(Complaint complaint) {
        log.info("[NO-OP EMAIL] Skipped overdue email for #{}",
                complaint.getId());
    }
}
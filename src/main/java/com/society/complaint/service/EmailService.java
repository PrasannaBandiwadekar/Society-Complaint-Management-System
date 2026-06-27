package com.society.complaint.service;

import com.society.complaint.entity.Complaint;

public interface EmailService {

    void sendComplaintCreatedEmail(Complaint complaint);

    void sendComplaintAssignedEmail(Complaint complaint);

    void sendStatusChangedEmail(Complaint complaint, String oldStatus, String newStatus);

    void sendOverdueEscalationEmail(Complaint complaint);
}

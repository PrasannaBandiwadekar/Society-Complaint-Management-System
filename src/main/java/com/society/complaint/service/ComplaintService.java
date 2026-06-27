package com.society.complaint.service;

import com.society.complaint.dto.ComplaintDto;
import com.society.complaint.entity.Complaint;
import com.society.complaint.entity.User;
import com.society.complaint.enums.ComplaintStatus;

import java.util.List;

public interface ComplaintService {

    Complaint createComplaint(ComplaintDto dto, User resident);

    Complaint getById(Long id);

    List<Complaint> getComplaintsForResident(User resident);

    List<Complaint> getComplaintsForWorker(User worker);

    List<Complaint> getAllComplaints();

    List<Complaint> getOverdueComplaints();

    Complaint assignWorker(Long complaintId, Long workerId, String changedByEmail);

    Complaint updateStatus(Long complaintId, ComplaintStatus newStatus, String remarks, String changedByEmail);

    // Called by the scheduled job
    void runSlaCheck();

    long countByStatus(ComplaintStatus status);

    long countOverdue();
}

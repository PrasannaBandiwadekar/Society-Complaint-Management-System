package com.society.complaint.service;

import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
@RequiredArgsConstructor
public class DashboardStatsServiceProd extends DashboardStatsService {

    private final ComplaintRepository complaintRepository;

    @Override
    public DashboardStats getStats() {
        return DashboardStats.builder()
                .total(complaintRepository.count())
                .open(complaintRepository.countByStatus(ComplaintStatus.OPEN))
                .assigned(complaintRepository.countByStatus(ComplaintStatus.ASSIGNED))
                .inProgress(complaintRepository.countByStatus(ComplaintStatus.IN_PROGRESS))
                .resolved(complaintRepository.countByStatus(ComplaintStatus.RESOLVED))
                .overdue(complaintRepository.countByOverdueTrue())
                .build();
    }

    @Override
    public void evictStatsCache() {
        // No cache in prod — nothing to evict
    }
}
package com.society.complaint.service;

import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatsService {

    private final ComplaintRepository complaintRepository;

    public static final String CACHE_NAME = "dashboard-stats";

    /**
     * In local Docker (with Redis), @Cacheable caches this result.
     * In prod (Render), this method runs a fresh DB query every time
     * since Redis isn't available — that's fine for a single-instance deployment.
     * The @Cacheable annotation is simply ignored when no CacheManager bean exists.
     */
    @Cacheable(value = CACHE_NAME, key = "'stats'")
    public DashboardStats getStats() {
        log.info("Loading dashboard stats from database");
        return DashboardStats.builder()
                .total(complaintRepository.count())
                .open(complaintRepository.countByStatus(ComplaintStatus.OPEN))
                .assigned(complaintRepository.countByStatus(ComplaintStatus.ASSIGNED))
                .inProgress(complaintRepository.countByStatus(ComplaintStatus.IN_PROGRESS))
                .resolved(complaintRepository.countByStatus(ComplaintStatus.RESOLVED))
                .overdue(complaintRepository.countByOverdueTrue())
                .build();
    }

    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictStatsCache() {
        log.info("Dashboard stats cache evicted");
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DashboardStats implements java.io.Serializable {
        private long total;
        private long open;
        private long assigned;
        private long inProgress;
        private long resolved;
        private long overdue;
    }
}
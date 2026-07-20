package com.society.complaint.service;

import com.society.complaint.enums.ComplaintStatus;
import com.society.complaint.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Caches the admin dashboard counts in Redis.
 *
 * Plain English: The admin dashboard shows 6 numbers — Total, Open, Assigned,
 * In Progress, Resolved, Overdue. Without caching, loading the dashboard runs
 * 6 separate COUNT queries against the database every time. With Redis caching:
 *
 * - First load: runs the 6 queries, stores results in Redis for 5 minutes.
 * - Next 20 loads in those 5 minutes: served from Redis in ~1ms, zero DB hits.
 * - After 5 minutes: cache expires automatically, next load refreshes it.
 * - When a complaint changes status: we explicitly evict (delete) the cache so
 *   the next dashboard load shows fresh numbers immediately, not stale ones.
 *
 * @Cacheable("dashboard-stats") = "before running this method, check Redis.
 *   If a value is already stored under 'dashboard-stats', return it immediately.
 *   If not, run the method, store the result in Redis, then return it."
 *
 * @CacheEvict(value = "dashboard-stats", allEntries = true) = "delete everything
 *   stored under 'dashboard-stats' in Redis" — called whenever a complaint changes
 *   so the next dashboard load gets fresh counts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatsService {

    private final ComplaintRepository complaintRepository;

    public static final String CACHE_NAME = "dashboard-stats";

    @Cacheable(value = CACHE_NAME, key = "'stats'")
    public DashboardStats getStats() {
        log.info("Cache MISS — loading dashboard stats from database");
        return DashboardStats.builder()
                .total(complaintRepository.count())
                .open(complaintRepository.countByStatus(ComplaintStatus.OPEN))
                .assigned(complaintRepository.countByStatus(ComplaintStatus.ASSIGNED))
                .inProgress(complaintRepository.countByStatus(ComplaintStatus.IN_PROGRESS))
                .resolved(complaintRepository.countByStatus(ComplaintStatus.RESOLVED))
                .overdue(complaintRepository.countByOverdueTrue())
                .build();
    }

    /**
     * Call this whenever a complaint is created, assigned, or its status changes —
     * so the admin dashboard always shows correct counts after an update.
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictStatsCache() {
        log.info("Dashboard stats cache evicted — next load will query the database");
    }

    // Inner record to hold all six dashboard counts as one cacheable object
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

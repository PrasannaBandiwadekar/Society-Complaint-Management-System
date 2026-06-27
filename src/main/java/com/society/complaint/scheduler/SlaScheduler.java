package com.society.complaint.scheduler;

import com.society.complaint.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlaScheduler {

    private final ComplaintService complaintService;

    /**
     * Runs every 5 minutes (300000 ms). In a real production system this
     * would run less frequently (e.g. every 15-30 min), but a short interval
     * here makes the feature easy to demo: raise a complaint with a short
     * SLA, wait a few minutes, and watch it get auto-flagged overdue with
     * an escalation email sent to admins.
     */
    @Scheduled(fixedRate = 300000)
    public void checkOverdueComplaints() {
        log.info("Running scheduled SLA check...");
        complaintService.runSlaCheck();
    }
}

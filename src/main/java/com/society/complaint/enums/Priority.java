package com.society.complaint.enums;

/**
 * Priority drives the SLA (Service Level Agreement) deadline.
 * Each priority level has a fixed number of hours within which
 * the complaint must be resolved before it is flagged "overdue".
 */
public enum Priority {
    LOW(72),       // 3 days
    MEDIUM(48),    // 2 days
    HIGH(24),      // 1 day
    CRITICAL(4);   // 4 hours - e.g. gas leak, electrical short circuit

    private final int slaHours;

    Priority(int slaHours) {
        this.slaHours = slaHours;
    }

    public int getSlaHours() {
        return slaHours;
    }
}

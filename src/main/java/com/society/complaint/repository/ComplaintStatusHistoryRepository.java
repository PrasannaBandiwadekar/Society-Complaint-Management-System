package com.society.complaint.repository;

import com.society.complaint.entity.ComplaintStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, Long> {
}

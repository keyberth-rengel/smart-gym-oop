package com.smartgym.repository;

import com.smartgym.domain.ProgressRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProgressRecordRepository extends JpaRepository<ProgressRecord, Long> {
    List<ProgressRecord> findByCustomerEmailOrderByDateAsc(String email);
}

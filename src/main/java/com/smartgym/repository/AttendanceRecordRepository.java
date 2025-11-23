package com.smartgym.repository;

import com.smartgym.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByEmailOrderByTimestampAsc(String email);
}

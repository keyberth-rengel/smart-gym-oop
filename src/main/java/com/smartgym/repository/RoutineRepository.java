package com.smartgym.repository;

import com.smartgym.domain.Routine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    List<Routine> findByCustomerEmailOrderByCreatedAtAsc(String email);
    Optional<Routine> findFirstByCustomerEmailOrderByCreatedAtDesc(String email);
}

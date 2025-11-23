package com.smartgym.repository;

import com.smartgym.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByTrainer_EmailAndSchedule_Date(String trainerEmail, LocalDate date);
    boolean existsByTrainer_EmailAndSchedule_DateAndSchedule_Time(String trainerEmail, LocalDate date, LocalTime time);
    boolean existsByCustomer_EmailAndTrainer_EmailAndSchedule_DateAndSchedule_Time(String customerEmail, String trainerEmail, LocalDate date, LocalTime time);
}

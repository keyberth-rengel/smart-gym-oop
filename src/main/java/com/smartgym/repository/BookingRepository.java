package com.smartgym.repository;

import com.smartgym.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByTrainer_EmailAndSchedule_Date(String trainerEmail, LocalDate date);
    List<Booking> findByTrainer_EmailAndSchedule_DateBetweenOrderBySchedule_DateAscSchedule_TimeAsc(
            String trainerEmail, LocalDate from, LocalDate to);

    /**
     * Clientes distintos con reservas del entrenador: una fila por cliente con su total de reservas y su reserva
     * más reciente (fecha y hora), ordenada de la más reciente a la más antigua. Sin cargar entidades en memoria.
     */
    @Query("""
            select new com.smartgym.repository.TrainerCustomerRow(
                c.email, c.name, c.age,
                (select count(b2) from Booking b2
                    where b2.trainer.email = :trainerEmail and b2.customer.email = c.email),
                b.schedule.date, b.schedule.time)
            from Booking b join b.customer c
            where b.trainer.email = :trainerEmail
              and not exists (select 1 from Booking b3
                    where b3.trainer.email = :trainerEmail and b3.customer.email = c.email
                      and (b3.schedule.date > b.schedule.date
                           or (b3.schedule.date = b.schedule.date and b3.schedule.time > b.schedule.time)))
            order by b.schedule.date desc, b.schedule.time desc
            """)
    List<TrainerCustomerRow> findTrainerCustomers(@Param("trainerEmail") String trainerEmail);

    boolean existsByTrainer_EmailAndSchedule_DateAndSchedule_Time(String trainerEmail, LocalDate date, LocalTime time);
    boolean existsByCustomer_EmailAndTrainer_EmailAndSchedule_DateAndSchedule_Time(String customerEmail, String trainerEmail, LocalDate date, LocalTime time);
}

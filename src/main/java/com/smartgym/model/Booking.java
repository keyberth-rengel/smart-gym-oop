package com.smartgym.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(
    name = "bookings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_trainer_schedule", columnNames = {"trainer_email", "date", "time"})
    }
)
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_email", referencedColumnName = "email", nullable = false)
    private com.smartgym.model.Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_email", referencedColumnName = "email", nullable = false)
    private com.smartgym.model.Trainer trainer;

    @Embedded
    private Schedule schedule;

    @Column(length = 255)
    private String note;

    @Embeddable
    public static class Schedule {
        @Column(name = "date", nullable = false)
        private LocalDate date;
        @Column(name = "time", nullable = false)
        private LocalTime time;

        protected Schedule() {
        }

        public Schedule(LocalDate date, LocalTime time) {
            if (date == null || time == null) {
                throw new IllegalArgumentException("Date and time are required.");
            }
            this.date = date;
            this.time = time;
        }

        public LocalDate getDate() { return date; }
        public LocalTime getTime() { return time; }

        @Override
        public String toString() { return date + " " + time; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Schedule)) return false;
            Schedule that = (Schedule) o;
            return Objects.equals(date, that.date) && Objects.equals(time, that.time);
        }

        @Override
        public int hashCode() { return Objects.hash(date, time); }
    }

    protected Booking() {
        // JPA
    }

    public Booking(Customer customer, Trainer trainer, Schedule schedule) {
        this(customer, trainer, schedule, null);
    }

    public Booking(Customer customer, Trainer trainer, Schedule schedule, String note) {
        if (customer == null || trainer == null || schedule == null) {
            throw new IllegalArgumentException("Customer, trainer and schedule are required.");
        }
        this.customer = customer;
        this.trainer = trainer;
        this.schedule = schedule;
        this.note = note;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public Trainer getTrainer() { return trainer; }
    public Schedule getSchedule() { return schedule; }
    public String getNote() { return note; }

    // Conveniencia para compatibilidad con controladores/DTOs existentes
    public String getCustomerEmail() { return customer != null ? customer.getEmail() : null; }
    public String getTrainerEmail() { return trainer != null ? trainer.getEmail() : null; }

    @Override
    public String toString() {
        return "Booking{" +
            "id=" + id +
            ", customer='" + getCustomerEmail() + '\'' +
            ", trainer='" + getTrainerEmail() + '\'' +
            ", schedule=" + schedule +
            (note != null ? ", note='" + note + '\'' : "") +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking)) return false;
        Booking booking = (Booking) o;
        return Objects.equals(getCustomerEmail(), booking.getCustomerEmail()) &&
            Objects.equals(getTrainerEmail(), booking.getTrainerEmail()) &&
                Objects.equals(schedule, booking.schedule);
    }

        @Override
        public int hashCode() { return Objects.hash(getCustomerEmail(), getTrainerEmail(), schedule); }
}
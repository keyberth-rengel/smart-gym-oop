package com.smartgym.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Booking {
    private static final AtomicInteger TOTAL_COUNT = new AtomicInteger(0);

    private final int id;
    private final String customerEmail;
    private final String trainerEmail;
    private final Schedule schedule;
    private final String note;

    public static class Schedule {
        private final LocalDate date;
        private final LocalTime time;

        public Schedule(LocalDate date, LocalTime time) {
            if (date == null || time == null) {
                throw new IllegalArgumentException("Se requieren fecha y hora.");
            }
            this.date = date;
            this.time = time;
        }

        public LocalDate getDate() { return date; }
        public LocalTime getTime() { return time; }

        @Override
        public String toString() {
            return date + " " + time;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Schedule)) return false;
            Schedule that = (Schedule) o;
            return Objects.equals(date, that.date) && Objects.equals(time, that.time);
        }

        @Override
        public int hashCode() {
            return Objects.hash(date, time);
        }
    }

    public Booking(String customerEmail, String trainerEmail, Schedule schedule) {
        this(customerEmail, trainerEmail, schedule, null);
    }

    public Booking(String customerEmail, String trainerEmail, Schedule schedule, String note) {
        if (customerEmail == null || trainerEmail == null || schedule == null) {
            throw new IllegalArgumentException("Se requiere cliente, entrenador y horario.");
        }
        this.id = TOTAL_COUNT.incrementAndGet();
        this.customerEmail = customerEmail.toLowerCase().trim();
        this.trainerEmail = trainerEmail.toLowerCase().trim();
        this.schedule = schedule;
        this.note = note;
    }

    public int getId() { return id; }
    public String getCustomerEmail() { return customerEmail; }
    public String getTrainerEmail() { return trainerEmail; }
    public Schedule getSchedule() { return schedule; }
    public String getNote() { return note; }

    public static int getTotalCount() { return TOTAL_COUNT.get(); }

    @Override
    public String toString() {
        return "Booking{" +
                "id=" + id +
                ", customer='" + customerEmail + '\'' +
                ", trainer='" + trainerEmail + '\'' +
                ", schedule=" + schedule +
                (note != null ? ", note='" + note + '\'' : "") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking)) return false;
        Booking booking = (Booking) o;
        return Objects.equals(customerEmail, booking.customerEmail) &&
                Objects.equals(trainerEmail, booking.trainerEmail) &&
                Objects.equals(schedule, booking.schedule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerEmail, trainerEmail, schedule);
    }
}
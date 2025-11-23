package com.smartgym.application;

import com.smartgym.domain.AttendanceRecord;
import com.smartgym.domain.PaymentMethod;
import com.smartgym.domain.ProgressRecord;
import com.smartgym.domain.Routine;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.repository.AttendanceRecordRepository;
import com.smartgym.repository.ProgressRecordRepository;
import com.smartgym.repository.RoutineRepository;
import com.smartgym.repository.IdentityLinkRepository;
import com.smartgym.service.SmartGymService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Component
public class GymExtensions {

    private final SmartGymService core;
    private final RoutineRepository routineRepository;
    private final ProgressRecordRepository progressRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final IdentityLinkRepository identityLinkRepository;

    public GymExtensions(SmartGymService core,
                         RoutineRepository routineRepository,
                         ProgressRecordRepository progressRepository,
                         AttendanceRecordRepository attendanceRepository,
                         IdentityLinkRepository identityLinkRepository) {
        this.core = core;
        this.routineRepository = routineRepository;
        this.progressRepository = progressRepository;
        this.attendanceRepository = attendanceRepository;
        this.identityLinkRepository = identityLinkRepository;
    }

    @Transactional
    public void registerCustomerIdentity(String dni, String email) {
        var key = normalize(dni);
        var value = normalize(email);
        identityLinkRepository.findById(key)
                .ifPresentOrElse(
                        il -> { il.setEmail(value); },
                        () -> identityLinkRepository.save(new com.smartgym.domain.IdentityLink(key, value))
                );
    }

    @Transactional
    public void registerTrainerIdentity(String dni, String email) {
        // Igual que cliente; mapeo único por DNI
        registerCustomerIdentity(dni, email);
    }

    public Optional<String> emailByDni(String dni) {
        return identityLinkRepository.findById(normalize(dni)).map(com.smartgym.domain.IdentityLink::getEmail);
    }

    @Transactional
    public void setPayment(String customerEmail, PaymentMethod pm) {
        var customer = core.findCustomer(customerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerEmail));
        customer.setPaymentMethod(pm);
        // Persistencia por dirty checking dentro de la transacción
    }

    public Optional<PaymentMethod> getPayment(String customerEmail) {
        return core.findCustomer(customerEmail).map(Customer::getPaymentMethod);
    }

    @Transactional
    public Routine assignRandomRoutine(String customerEmail) {
        var customer = core.findCustomer(customerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerEmail));
        Routine r = new Routine(customer, randomWeeklyPlan());
        return routineRepository.save(r);
    }

    public List<Routine> routineHistory(String customerEmail) {
        return routineRepository.findByCustomerEmailOrderByCreatedAtAsc(normalize(customerEmail));
    }

    @Transactional(readOnly = true)
    public Optional<Routine> activeRoutine(String customerEmail) {
        return routineRepository.findFirstByCustomerEmailOrderByCreatedAtDesc(normalize(customerEmail));
    }

    @Transactional
    public String accessByDni(String dni) {
        String key = normalize(dni);
        String email = emailByDni(key).orElse(null);
        if (email == null) throw new IllegalArgumentException("DNI not linked");

        var roleOpt =
                core.findCustomer(email).map(c -> AttendanceRecord.Role.CUSTOMER)
                        .or(() -> core.findTrainer(email).map(t -> AttendanceRecord.Role.TRAINER));

        if (roleOpt.isEmpty()) throw new IllegalArgumentException("Identity not recognized for the linked email");
        var role = roleOpt.get();

        attendanceRepository.save(new AttendanceRecord(email, role));

        String name = (role == AttendanceRecord.Role.CUSTOMER)
                ? core.findCustomer(email).map(Customer::getName).orElse("Customer")
                : core.findTrainer(email).map(Trainer::getName).orElse("Trainer");

        return "Welcome " + name + "! Access recorded for " + email + ".";
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecord> attendanceByEmail(String email) {
        return attendanceRepository.findByEmailOrderByTimestampAsc(normalize(email));
    }

    @Transactional
    public void addProgressByDni(String dni, double weightKg, double bodyFatPct, double musclePct) {
        String email = emailByDni(dni).orElseThrow(() -> new IllegalArgumentException("DNI not linked"));
        var customer = core.findCustomer(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + email));
        progressRepository.save(new ProgressRecord(customer, LocalDate.now(), weightKg, bodyFatPct, musclePct));
    }

    @Transactional(readOnly = true)
    public List<ProgressRecord> progressByDni(String dni) {
        String email = emailByDni(dni).orElseThrow(() -> new IllegalArgumentException("DNI not linked"));
        return progressRepository.findByCustomerEmailOrderByDateAsc(normalize(email));
    }

    private Map<DayOfWeek, String> randomWeeklyPlan() {
        List<String> blocks = new ArrayList<>(List.of("Legs", "Chest", "Back", "Shoulders", "Arms", "Cardio"));
        Collections.shuffle(blocks);
        EnumMap<DayOfWeek, String> plan = new EnumMap<>(DayOfWeek.class);
        plan.put(DayOfWeek.MONDAY,    blocks.get(0));
        plan.put(DayOfWeek.TUESDAY,   blocks.get(1));
        plan.put(DayOfWeek.WEDNESDAY, blocks.get(2));
        plan.put(DayOfWeek.THURSDAY,  blocks.get(3));
        plan.put(DayOfWeek.FRIDAY,    blocks.get(4));
        plan.put(DayOfWeek.SATURDAY,  blocks.get(5));
        return plan;
    }

    private String normalize(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }
}
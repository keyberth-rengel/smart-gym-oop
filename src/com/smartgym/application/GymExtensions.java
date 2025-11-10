package com.smartgym.application;

import com.smartgym.domain.AttendanceRecord;
import com.smartgym.domain.PaymentMethod;
import com.smartgym.domain.ProgressRecord;
import com.smartgym.domain.Routine;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.service.SmartGymService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

public class GymExtensions {

    private final SmartGymService core;

    private final Map<String, String> dniToEmail = new HashMap<>();
    private final Map<String, PaymentMethod> payments = new HashMap<>();
    private final Map<String, List<Routine>> routinesByCustomer = new HashMap<>();
    private final List<AttendanceRecord> attendance = new ArrayList<>();
    private final Map<String, List<ProgressRecord>> progressByCustomer = new HashMap<>();

    public GymExtensions(SmartGymService core) {
        this.core = core;
    }

    public void registerCustomerIdentity(String dni, String email) {
        dniToEmail.put(normalize(dni), normalize(email));
    }

    public void registerTrainerIdentity(String dni, String email) {
        dniToEmail.put(normalize(dni), normalize(email));
    }

    public Optional<String> emailByDni(String dni) {
        return Optional.ofNullable(dniToEmail.get(normalize(dni)));
    }

    public void setPayment(String customerEmail, PaymentMethod pm) {
        payments.put(normalize(customerEmail), pm);
    }

    public Optional<PaymentMethod> getPayment(String customerEmail) {
        return Optional.ofNullable(payments.get(normalize(customerEmail)));
    }

    public Routine assignRandomRoutine(String customerEmail) {
        String key = normalize(customerEmail);
        Routine r = new Routine(randomWeeklyPlan());
        routinesByCustomer.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        return r;
    }

    public List<Routine> routineHistory(String customerEmail) {
        return routinesByCustomer.getOrDefault(normalize(customerEmail), List.of());
    }

    public Optional<Routine> activeRoutine(String customerEmail) {
        var list = routinesByCustomer.get(normalize(customerEmail));
        if (list == null || list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(list.size() - 1));
    }

    public String accessByDni(String dni) {
        String key = normalize(dni);
        String email = dniToEmail.get(key);
        if (email == null) return "No se encontró identidad para ese DNI.";

        var role = core.findCustomer(email).map(c -> AttendanceRecord.Role.CUSTOMER)
                .or(() -> core.findTrainer(email).map(t -> AttendanceRecord.Role.TRAINER))
                .orElse(null);

        if (role == null) return "El DNI existe pero no corresponde a cliente/entrenador registrado.";

        attendance.add(new AttendanceRecord(email, role));
        String name = role == AttendanceRecord.Role.CUSTOMER
                ? core.findCustomer(email).map(Customer::getName).orElse("Cliente")
                : core.findTrainer(email).map(Trainer::getName).orElse("Entrenador");

        return "¡Bienvenido/a " + name + "! Acceso registrado para " + email + ".";
    }

    public List<AttendanceRecord> attendanceByEmail(String email) {
        String key = normalize(email);
        List<AttendanceRecord> out = new ArrayList<>();
        for (var a : attendance) {
            if (a.getEmail().equals(key)) out.add(a);
        }
        return out;
    }

    public void addProgressByDni(String dni, double weightKg, double bodyFatPct, double musclePct) {
        String email = emailByDni(dni).orElseThrow(() -> new IllegalArgumentException("DNI no registrado."));
        String key = normalize(email);
        progressByCustomer.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new ProgressRecord(LocalDate.now(), weightKg, bodyFatPct, musclePct));
    }

    public List<ProgressRecord> progressByDni(String dni) {
        String email = emailByDni(dni).orElseThrow(() -> new IllegalArgumentException("DNI no registrado."));
        return progressByCustomer.getOrDefault(normalize(email), List.of());
    }

    private Map<DayOfWeek, String> randomWeeklyPlan() {
        List<String> blocks = new ArrayList<>(List.of("Pierna", "Pecho", "Espalda", "Hombro", "Brazos", "Cardio"));
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
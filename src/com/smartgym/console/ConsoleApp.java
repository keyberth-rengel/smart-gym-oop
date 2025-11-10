package com.smartgym.console;

import com.smartgym.application.GymExtensions;
import com.smartgym.domain.PaymentMethod;
import com.smartgym.domain.Routine;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.service.SmartGymService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Scanner;

public class ConsoleApp {
    private final SmartGymService service;
    private final Scanner in;
    private final GymExtensions ext;

    public ConsoleApp(SmartGymService service) {
        this.service = service;
        this.in = new Scanner(System.in);
        this.ext = new GymExtensions(service);
        seed(); // datos de ejemplo
    }

    public void run() {
        while (true) {
            System.out.println("\n=== SmartGym (Consola) ===");
            System.out.println("1) Registrar cliente");
            System.out.println("2) Registrar entrenador");
            System.out.println("3) Crear reserva");
            System.out.println("4) Listar reservas");
            System.out.println("5) Listar reservas de un entrenador (por fecha)");
            System.out.println("6) Cancelar reserva (por ID)");
            System.out.println("7) Buscar cliente (por email)");
            System.out.println("8) Buscar entrenador (por email)");
            System.out.println("9) Ver historial de un cliente");
            System.out.println("10) Registrar acceso por DNI");
            System.out.println("11) Ver asistencias por DNI");
            System.out.println("12) Registrar progreso por DNI");
            System.out.println("13) Ver progreso por DNI");
            System.out.println("14) Historial de rutinas por DNI");
            System.out.println("15) Cambiar rutina (aleatoria) por DNI");
            System.out.println("16) Ver rutina por día (por DNI)");
            System.out.println("0) Salir");
            System.out.print("Elige una opción: ");

            String opt = in.nextLine().trim();
            switch (opt) {
                case "1": addCustomer(); break;
                case "2": addTrainer(); break;
                case "3": createBooking(); break;
                case "4": listBookings(); break;
                case "5": listTrainerBookings(); break;
                case "6": cancelBooking(); break;
                case "7": findCustomer(); break;
                case "8": findTrainer(); break;
                case "9": showCustomerHistory(); break;
                case "10": accessByDni(); break;
                case "11": showAttendanceByDni(); break;
                case "12": addProgressByDni(); break;
                case "13": showProgressByDni(); break;
                case "14": showRoutineHistoryByDni(); break;
                case "15": changeRoutineByDni(); break;
                case "16": showRoutineForChosenDay(); break;

                case "0": System.out.println("¡Hasta luego!"); return;
                default: System.out.println("Opción inválida.");
            }
        }
    }

    private void addCustomer() {
        System.out.print("Correo del cliente: ");
        String email = in.nextLine().trim();
        if (service.findCustomer(email).isPresent()) {
            System.out.println("Ya existe un cliente con ese correo.");
            return;
        }

        System.out.print("Nombre: ");
        String name = in.nextLine().trim();

        System.out.print("Edad: ");
        int age;
        try { age = Integer.parseInt(in.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Error: la edad debe ser un número entero."); return; }

        try {
            service.addCustomer(new Customer(email, name, age));
            System.out.print("DNI del cliente: ");
            String dni = in.nextLine().trim();
            if (ext.emailByDni(dni).isPresent()) {
                System.out.println("DNI ya registrado para otro usuario.");
                return;
            }
            ext.registerCustomerIdentity(dni, email);

            System.out.print("Número de tarjeta (simulación): ");
            String card = in.nextLine().trim();
            ext.setPayment(email, new PaymentMethod(card));
            System.out.println("Pago registrado: " + ext.getPayment(email).map(PaymentMethod::masked).orElse("N/A"));

            var r = ext.assignRandomRoutine(email);
            System.out.println("Rutina asignada (L-S): " + r.getPlan());

            System.out.println("Cliente registrado.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void addTrainer() {
        System.out.print("Correo del entrenador: ");
        String email = in.nextLine().trim();
        if (service.findTrainer(email).isPresent()) {
            System.out.println("Ya existe un entrenador con ese correo.");
            return;
        }

        System.out.print("Nombre: ");
        String name = in.nextLine().trim();
        System.out.print("Edad: ");
        int age;
        try { age = Integer.parseInt(in.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Error: la edad debe ser un número entero."); return; }

        System.out.print("Especialidad: ");
        String spec = in.nextLine().trim();

        try {
            service.addTrainer(new Trainer(email, name, age, spec));
            System.out.print("DNI del entrenador (para control de acceso): ");
            String dni = in.nextLine().trim();
            if (ext.emailByDni(dni).isPresent()) {
                System.out.println("DNI ya registrado para otro usuario.");
                return;
            }
            ext.registerTrainerIdentity(dni, email);
            System.out.println("Entrenador registrado.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void createBooking() {
        System.out.print("Correo del cliente: ");
        String c = in.nextLine().trim();
        if (service.findCustomer(c).isEmpty()) {
            System.out.println("El cliente no existe: " + c);
            return;
        }

        System.out.print("Correo del entrenador: ");
        String t = in.nextLine().trim();
        if (service.findTrainer(t).isEmpty()) {
            System.out.println("El entrenador no existe: " + t);
            return;
        }

        System.out.print("Fecha (AAAA-MM-DD): ");
        LocalDate d;
        try { d = LocalDate.parse(in.nextLine().trim()); }
        catch (Exception e) { System.out.println("Error: formato de fecha inválido (usa AAAA-MM-DD)."); return; }

        System.out.print("Hora (HH:MM): ");
        LocalTime tm;
        try { tm = LocalTime.parse(in.nextLine().trim()); }
        catch (Exception e) { System.out.println("Error: formato de hora inválido (usa HH:MM)."); return; }

        System.out.print("Nota (opcional, deja en blanco para omitir): ");
        String note = in.nextLine();
        if (note != null) note = note.trim();
        if (note != null && note.isBlank()) note = null;

        try {
            var b = (note == null)
                    ? service.createBooking(c, t, d, tm)
                    : service.createBooking(c, t, d, tm, note);
            System.out.println("Reserva creada (ID " + b.getId() + ").");
            System.out.println(b);
        } catch (Exception e) {
            System.out.println("No se pudo crear la reserva: " + e.getMessage());
        }
    }

    private void listBookings() {
        var all = service.listBookings();
        if (all.isEmpty()) System.out.println("(Sin reservas)");
        else {
            System.out.println("Reservas totales: " + all.size());
            all.forEach(System.out::println);
        }
    }

    private void listTrainerBookings() {
        System.out.print("Correo del entrenador: ");
        String t = in.nextLine().trim();
        System.out.print("Fecha (AAAA-MM-DD): ");
        LocalDate d;
        try { d = LocalDate.parse(in.nextLine().trim()); }
        catch (Exception e) { System.out.println("Error: formato de fecha inválido (usa AAAA-MM-DD)."); return; }
        var list = service.listTrainerBookings(t, d);
        if (list.isEmpty()) System.out.println("(Sin reservas para ese entrenador en esa fecha)");
        else list.forEach(System.out::println);
    }

    private void cancelBooking() {
        System.out.print("ID de la reserva: ");
        int id;
        try { id = Integer.parseInt(in.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Error: el ID debe ser un número entero."); return; }
        try {
            boolean ok = service.cancelBooking(id);
            System.out.println(ok ? "Reserva " + id + " cancelada." : "No se pudo cancelar.");
        } catch (Exception e) {
            System.out.println("No se pudo cancelar: " + e.getMessage());
        }
    }

    private void findCustomer() {
        System.out.print("Correo del cliente: ");
        String email = in.nextLine().trim();
        service.findCustomer(email)
                .ifPresentOrElse(
                        c -> System.out.println("Encontrado: " + c),
                        () -> System.out.println("No se encontró un cliente con ese email.")
                );
    }

    private void findTrainer() {
        System.out.print("Correo del entrenador: ");
        String email = in.nextLine().trim();
        service.findTrainer(email)
                .ifPresentOrElse(
                        t -> System.out.println("Encontrado: " + t),
                        () -> System.out.println("No se encontró un entrenador con ese email.")
                );
    }

    private void showCustomerHistory() {
        System.out.print("Correo del cliente: ");
        String email = in.nextLine().trim();
        var history = service.getCustomerHistory(email);
        if (history.isEmpty()) System.out.println("Sin historial o cliente inexistente.");
        else {
            System.out.println("Historial:");
            history.forEach(System.out::println);
        }
    }

    private void accessByDni() {
        System.out.print("DNI: ");
        String dni = in.nextLine().trim();
        String msg = ext.accessByDni(dni);
        System.out.println(msg);
    }

    private void showAttendanceByDni() {
        System.out.print("DNI: ");
        String dni = in.nextLine().trim();

        var emailOpt = ext.emailByDni(dni);
        if (emailOpt.isEmpty()) {
            System.out.println("DNI no registrado.");
            return;
        }

        var list = ext.attendanceByEmail(emailOpt.get());
        if (list.isEmpty()) {
            System.out.println("(Sin asistencias)");
        } else {
            System.out.println("Asistencias registradas:");
            list.forEach(a -> System.out.println(
                    " - " + a.getRole() + " | " + a.getEmail() + " | " + a.getTimestamp()
            ));
        }
    }

    private void addProgressByDni() {
        System.out.print("DNI del cliente: ");
        String dni = in.nextLine().trim();

        var emailOpt = ext.emailByDni(dni);
        if (emailOpt.isEmpty()) {
            System.out.println("DNI no registrado.");
            return;
        }

        try {
            System.out.print("Peso (kg): ");
            double w = Double.parseDouble(in.nextLine().trim());
            System.out.print("Grasa corporal (%): ");
            double bf = Double.parseDouble(in.nextLine().trim());
            System.out.print("Musculatura (%): ");
            double ms = Double.parseDouble(in.nextLine().trim());

            ext.addProgressByDni(dni, w, bf, ms);
            System.out.println("Progreso registrado.");
        } catch (NumberFormatException e) {
            System.out.println("Error: debes ingresar números válidos.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void showProgressByDni() {
        System.out.print("DNI del cliente: ");
        String dni = in.nextLine().trim();
        try {
            var list = ext.progressByDni(dni);
            if (list.isEmpty()) {
                System.out.println("(Sin registros)");
                return;
            }

            list.forEach(System.out::println);
            System.out.println("Total de registros: " + list.size());

            double avgW  = list.stream().mapToDouble(p -> p.getWeightKg()).average().orElse(0);
            double avgBF = list.stream().mapToDouble(p -> p.getBodyFatPct()).average().orElse(0);
            double avgM  = list.stream().mapToDouble(p -> p.getMusclePct()).average().orElse(0);

            System.out.printf("Promedios -> Peso: %.2f kg, Grasa: %.2f %%, Musculatura: %.2f %%\n",
                    avgW, avgBF, avgM);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void showRoutineHistoryByDni() {
        System.out.print("DNI del cliente: ");
        String dni = in.nextLine().trim();
        var email = ext.emailByDni(dni).orElse(null);
        if (email == null) { System.out.println("DNI no registrado."); return; }

        var history = ext.routineHistory(email);
        if (history.isEmpty()) { System.out.println("(Sin rutinas)"); return; }

        for (int i = 0; i < history.size(); i++) {
            var r = history.get(i);
            String tag = (i == history.size() - 1) ? " (ACTIVA)" : "";
            System.out.println("#" + (i + 1) + tag + " -> " + r.getPlan());
        }
    }

    private void changeRoutineByDni() {
        System.out.print("DNI del cliente: ");
        String dni = in.nextLine().trim();
        var email = ext.emailByDni(dni).orElse(null);
        if (email == null) { System.out.println("DNI no registrado."); return; }

        var r = ext.assignRandomRoutine(email);
        System.out.println("Nueva rutina activa (L-S): " + r.getPlan());
    }

    private void showRoutineForChosenDay() {
        System.out.print("DNI del cliente: ");
        String dni = in.nextLine().trim();
        var email = ext.emailByDni(dni).orElse(null);
        if (email == null) { System.out.println("DNI no registrado."); return; }

        var active = ext.activeRoutine(email);
        if (active.isEmpty()) { System.out.println("Sin rutina activa."); return; }

        System.out.println("Elige día (1=Lunes, 2=Martes, 3=Miércoles, 4=Jueves, 5=Viernes, 6=Sábado): ");
        String opt = in.nextLine().trim();
        int n;
        try { n = Integer.parseInt(opt); }
        catch (NumberFormatException e) { System.out.println("Opción inválida."); return; }
        if (n < 1 || n > 6) { System.out.println("Opción inválida."); return; }

        DayOfWeek day = DayOfWeek.of(n);
        String block = active.get().getFor(day);
        System.out.println("Hoy te toca: " + (block == null ? "(descanso)" : block));
    }

    private void seed() {
        service.addCustomer(new Customer("alice@example.com", "Alice", 28));
        service.addCustomer(new Customer("bob@example.com", "Bob", 35));
        service.addTrainer(new Trainer("mike@smartgym.com", "Mike", 40, "Fuerza"));
        service.addTrainer(new Trainer("sara@smartgym.com", "Sara", 32, "Cardio"));

        // DNIs de ejemplo
        ext.registerCustomerIdentity("11111111", "alice@example.com");
        ext.registerCustomerIdentity("22222222", "bob@example.com");
        ext.registerTrainerIdentity("33333333", "mike@smartgym.com");
        ext.registerTrainerIdentity("44444444", "sara@smartgym.com");
    }
}
package com.smartgym.console;

import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.service.SmartGymService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Scanner;

public class ConsoleApp {
    private final SmartGymService service;
    private final Scanner in;

    public ConsoleApp(SmartGymService service) {
        this.service = service;
        this.in = new Scanner(System.in);
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
                case "0":
                    System.out.println("¡Hasta luego!");
                    return;
                default:
                    System.out.println("Opción inválida.");
            }
        }
    }

    private void addCustomer() {
        System.out.print("Correo del cliente: ");
        String email = in.nextLine().trim();
        System.out.print("Nombre: ");
        String name = in.nextLine().trim();
        System.out.print("Edad: ");
        int age;
        try {
            age = Integer.parseInt(in.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Error: la edad debe ser un número entero.");
            return;
        }

        try {
            service.addCustomer(new Customer(email, name, age));
            System.out.println("Cliente registrado.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void addTrainer() {
        System.out.print("Correo del entrenador: ");
        String email = in.nextLine().trim();
        System.out.print("Nombre: ");
        String name = in.nextLine().trim();
        System.out.print("Edad: ");
        int age;
        try {
            age = Integer.parseInt(in.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Error: la edad debe ser un número entero.");
            return;
        }
        System.out.print("Especialidad: ");
        String spec = in.nextLine();

        try {
            service.addTrainer(new Trainer(email, name, age, spec));
            System.out.println("Entrenador registrado.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void createBooking() {
        System.out.print("Correo del cliente: ");
        String c = in.nextLine();
        System.out.print("Correo del entrenador: ");
        String t = in.nextLine();
        System.out.print("Fecha (AAAA-MM-DD): ");
        LocalDate d;
        try {
            d = LocalDate.parse(in.nextLine().trim());
        } catch (Exception e) {
            System.out.println("Error: formato de fecha inválido (usa AAAA-MM-DD).");
            return;
        }
        System.out.print("Hora (HH:MM): ");
        LocalTime tm;
        try {
            tm = LocalTime.parse(in.nextLine().trim());
        } catch (Exception e) {
            System.out.println("Error: formato de hora inválido (usa HH:MM).");
            return;
        }
        System.out.print("Nota (opcional, deja en blanco para omitir): ");
        String note = in.nextLine();
        if (note != null) note = note.trim();
        if (note != null && note.isBlank()) note = null;

        try {
            var b = service.createBooking(c, t, d, tm, note);
            System.out.println("Reserva creada (ID " + b.getId() + ").");
            System.out.println(b);
        } catch (Exception e) {
            System.out.println("No se pudo crear la reserva: " + e.getMessage());
        }
    }

    private void listBookings() {
        var all = service.listBookings();
        if (all.isEmpty()) {
            System.out.println("(Sin reservas)");
        } else {
            System.out.println("Reservas totales: " + all.size());
            all.forEach(System.out::println);
        }
    }

    private void listTrainerBookings() {
        System.out.print("Correo del entrenador: ");
        String t = in.nextLine();
        System.out.print("Fecha (AAAA-MM-DD): ");
        LocalDate d;
        try {
            d = LocalDate.parse(in.nextLine());
        } catch (Exception e) {
            System.out.println("Error: formato de fecha inválido (usa AAAA-MM-DD).");
            return;
        }
        var list = service.listTrainerBookings(t, d);
        if (list.isEmpty()) {
            System.out.println("(Sin reservas para ese entrenador en esa fecha)");
        } else {
            list.forEach(System.out::println);
        }
    }

    private void cancelBooking() {
        System.out.print("ID de la reserva: ");
        int id;
        try {
            id = Integer.parseInt(in.nextLine());
        } catch (NumberFormatException e) {
            System.out.println("Error: el ID debe ser un número entero.");
            return;
        }
        try {
            boolean ok = service.cancelBooking(id);
            System.out.println(ok ? "Reserva " + id + " cancelada." : "No se pudo cancelar.");
        } catch (Exception e) {
            System.out.println("No se pudo cancelar: " + e.getMessage());
        }
    }

    private void findCustomer() {
        System.out.print("Correo del cliente: ");
        String email = in.nextLine();
        service.findCustomer(email)
                .ifPresentOrElse(
                        c -> System.out.println("Encontrado: " + c),
                        () -> System.out.println("No se encontró un cliente con ese email.")
                );
    }

    private void findTrainer() {
        System.out.print("Correo del entrenador: ");
        String email = in.nextLine();
        service.findTrainer(email)
                .ifPresentOrElse(
                        t -> System.out.println("Encontrado: " + t),
                        () -> System.out.println("No se encontró un entrenador con ese email.")
                );
    }

    private void showCustomerHistory() {
        System.out.print("Email del cliente: ");
        String email = in.nextLine();
        var history = service.getCustomerHistory(email);
        if (history.isEmpty()) {
            System.out.println("Sin historial o cliente inexistente.");
        } else {
            System.out.println("Historial:");
            history.forEach(System.out::println);
        }
    }

    private void seed() {
        service.addCustomer(new Customer("alice@example.com", "Alice", 28));
        service.addCustomer(new Customer("bob@example.com", "Bob", 35));
        service.addTrainer(new Trainer("mike@smartgym.com", "Mike", 40, "Fuerza"));
        service.addTrainer(new Trainer("sara@smartgym.com", "Sara", 32, "Cardio"));
    }
}
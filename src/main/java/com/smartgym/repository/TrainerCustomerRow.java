package com.smartgym.repository;

import java.time.LocalDate;
import java.time.LocalTime;

/** Fila de la consulta "clientes de un entrenador": cliente, cantidad de reservas y su reserva más reciente. */
public record TrainerCustomerRow(String email, String name, int age, long sessions,
                                 LocalDate lastDate, LocalTime lastTime) {}

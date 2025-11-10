package com.smartgym.api.dto;

public record BookingResponse(
        int id, String customerEmail, String trainerEmail, String date, String time, String note
) {}
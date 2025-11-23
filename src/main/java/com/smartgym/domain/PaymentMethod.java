package com.smartgym.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class PaymentMethod {
    @Column(name = "card_number", length = 32)
    private String cardNumber;

    protected PaymentMethod() { }

    public PaymentMethod(String cardNumber) {
        if (cardNumber == null) {
            this.cardNumber = "";
        } else {
            String trimmed = cardNumber.trim();
            // Guardar solo últimos 4 para minimizar exposición
            this.cardNumber = trimmed.length() > 4 ? trimmed.substring(trimmed.length() - 4) : trimmed;
        }
    }

    public String getCardNumber() { return cardNumber; }

    public String masked() {
        if (cardNumber.isEmpty()) return "(no-card)";
        return "**** **** **** " + cardNumber;
    }

    @Override
    public String toString() {
        return "PaymentMethod{" + masked() + "}";
    }
}
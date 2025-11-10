package com.smartgym.domain;

public class PaymentMethod {
    private final String cardNumber;

    public PaymentMethod(String cardNumber) {
        this.cardNumber = cardNumber == null ? "" : cardNumber.trim();
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String masked() {
        String last4 = cardNumber.length() >= 4 ? cardNumber.substring(cardNumber.length() - 4) : cardNumber;
        return "**** **** **** " + last4;
    }

    @Override
    public String toString() {
        return "PaymentMethod{card=" + masked() + "}";
    }
}
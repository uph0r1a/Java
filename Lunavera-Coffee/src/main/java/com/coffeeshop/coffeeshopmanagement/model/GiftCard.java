package com.coffeeshop.coffeeshopmanagement.model;

import java.time.LocalDateTime;

/** A prepaid gift card (TODO 7): its code, what was put on it, what is left, who sold it and when. Amounts are whole dong. */
public record GiftCard(int id, String code, long initialAmount, long balance, String soldBy, LocalDateTime createdAt) {
}

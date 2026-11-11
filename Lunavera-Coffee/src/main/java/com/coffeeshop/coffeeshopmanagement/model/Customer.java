package com.coffeeshop.coffeeshopmanagement.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Customer {
    private int id;
    private String fullName;
    private String phone;
    private String email;
    private int loyaltyPoints;
    private int lifetimePoints;
    private LocalDateTime createdAt;
    /** When the customer's personal details were removed on request (TODO 3.4, rule D21); null = not made anonymous. */
    private LocalDateTime anonymisedAt;

    /** Optional date of birth, for the birthday badge (TODO 7, rule D22); null = not given, and always null once anonymised. */
    private LocalDate birthday;
    private LocalDate birthdayUsedOn;

    public Customer() {
    }

    public Customer(int id, String fullName, String phone, String email, int loyaltyPoints,
                     LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.loyaltyPoints = loyaltyPoints;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }
    /** The points earned over time, net of what refunds took back; spending points does not lower it (TODO 3.80). */
    public int getLifetimePoints() { return lifetimePoints; }
    public void setLifetimePoints(int lifetimePoints) { this.lifetimePoints = lifetimePoints; }
    /** The number the membership tier is read from: the points earned over time, and never less than the balance (an imported balance counts). */
    public int getTierPoints() { return Math.max(lifetimePoints, loyaltyPoints); }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDate getBirthday() { return birthday; }
    /** The day the birthday discount was given to this customer (TODO 3.66, rule D25), or null; never set for an anonymous customer. */
    public LocalDate getBirthdayUsedOn() { return birthdayUsedOn; }
    public void setBirthdayUsedOn(LocalDate birthdayUsedOn) { this.birthdayUsedOn = birthdayUsedOn; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }

    public LocalDateTime getAnonymisedAt() { return anonymisedAt; }
    public void setAnonymisedAt(LocalDateTime anonymisedAt) { this.anonymisedAt = anonymisedAt; }

    /** True once the customer asked to be forgotten: no name, phone or e-mail is left, only the orders that point at the row. */
    public boolean isAnonymised() { return anonymisedAt != null; }

    @Override
    public String toString() {
        // Used by ChoiceDialog/ComboBox cells elsewhere in the UI (e.g. attaching a customer
        // to an order in the POS screen).
        return phone != null && !phone.isBlank() ? fullName + " - " + phone : fullName;
    }
}

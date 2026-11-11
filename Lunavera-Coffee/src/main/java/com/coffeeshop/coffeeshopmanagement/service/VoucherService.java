package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.VoucherDAO;
import com.coffeeshop.coffeeshopmanagement.model.Voucher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What the voucher screens ask for (TODO 6.21): the list, making one, switching one, and looking a code up at the till. {@link VoucherRules}
 * holds the rules and {@link VoucherDAO} the permission (only an administrator makes or switches one), the unique code, and the audit line
 * written in the same transaction. {@code VoucherDialog} and {@code PromotionPanel} hold no DAO of their own.
 */
public class VoucherService {

    private final VoucherDAO vouchers;

    public VoucherService() {
        this(new VoucherDAO());
    }

    VoucherService(VoucherDAO vouchers) {
        this.vouchers = vouchers;
    }

    public List<Voucher> findAll() {
        return vouchers.findAll();
    }

    public Optional<Voucher> findByCode(String typed) {
        return vouchers.findByCode(typed);
    }

    public Voucher add(String code, String kind, Long value, BigDecimal minSubtotal, LocalDate validFrom, LocalDate validTo, Integer maxUses,
                       boolean oncePerCustomer) {
        return vouchers.add(code, kind, value, minSubtotal, validFrom, validTo, maxUses, oncePerCustomer);
    }

    public void setActive(int id, boolean active) {
        vouchers.setActive(id, active);
    }
}

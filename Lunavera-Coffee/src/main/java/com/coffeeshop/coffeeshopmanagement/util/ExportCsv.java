package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.OrderSummary;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** CSV text of orders and customers for an accountant (TODO 4.21). Every cell goes through {@link Csv#cell}. */
public final class ExportCsv {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ExportCsv() {}

    public static List<String> orders(List<OrderSummary> rows) {
        List<String> lines = new ArrayList<>();
        lines.add("Mã đơn,Thời gian tạo,Thời gian thanh toán,Nhân viên,Khách hàng,Thanh toán,Giảm giá (đ),Lý do giảm giá,Tổng tiền (đ),Trạng thái");
        for (OrderSummary row : rows) {
            Order o = row.order();
            lines.add(String.join(",",
                    String.valueOf(o.getId()),
                    o.getOrderDate() == null ? "" : o.getOrderDate().format(TIME),
                    o.getPaidAt() == null ? "" : o.getPaidAt().format(TIME),
                    Csv.cell(row.employeeName()),
                    Csv.cell(row.customerName() == null ? "Khách lẻ" : row.customerName()),
                    Csv.cell(PaymentMethod.labelOf(o.getPaymentMethod())),
                    o.getDiscount() == null ? "0" : o.getDiscount().toPlainString(),
                    Csv.cell(o.getDiscountReason()),
                    o.getTotal() == null ? "0" : o.getTotal().toPlainString(),
                    Csv.cell(statusText(o))));
        }
        return lines;
    }

    public static List<String> customers(List<Customer> customers) {
        List<String> lines = new ArrayList<>();
        lines.add("Họ và tên,Số điện thoại,Email,Điểm tích lũy,Ngày tạo");
        for (Customer c : customers) {
            lines.add(String.join(",",
                    Csv.cell(c.getFullName()),
                    phone(c.getPhone()),
                    Csv.cell(c.getEmail()),
                    String.valueOf(c.getLoyaltyPoints()),
                    c.getCreatedAt() == null ? "" : c.getCreatedAt().format(TIME)));
        }
        return lines;
    }

    /**
     * Excel drops the leading 0 of a phone number it reads as a number. A number made only of digits
     * (and an optional +) is written as ="0901234567", which Excel shows as text; anything else is an ordinary cell.
     */
    static String phone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        return phone.matches("\\+?\\d{6,15}") ? "=\"" + phone + "\"" : Csv.cell(phone);
    }

    private static String statusText(Order o) {
        return switch (o.getStatus()) {
            case PAID -> "Đã thanh toán";
            case CANCELLED -> "Đã hủy";
            case OPEN -> "Đang mở";
        };
    }

    /** UTF-8 with a byte order mark so Excel shows Vietnamese letters correctly; lines end with CRLF. */
    public static void write(Path file, List<String> lines) throws IOException {
        Files.writeString(file, "﻿" + String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
    }
}

package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.util.Money;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * The four columns that hold a line's size and extras (TODO 4.47) in one place, so every statement that writes or reads an order
 * line adds the same four things the same way: {@code size_name}, {@code size_percent}, {@code extras} and {@code option_cost}.
 */
final class OrderLineColumns {

    /** To append to the column list of an INSERT, after the last column. */
    static final String INSERT_COLUMNS = ", size_name, size_percent, extras, option_cost";
    /** To append to the VALUES of the same INSERT. */
    static final String INSERT_MARKS = ", ?, ?, ?, ?";
    /** To append to the column list of a SELECT. */
    static final String SELECT_COLUMNS = ", size_name, size_percent, extras, option_cost";

    private OrderLineColumns() {
    }

    /** Sets the four values starting at parameter {@code first}; returns the next free parameter number. */
    static int bind(PreparedStatement statement, int first, OrderItem line) throws SQLException {
        statement.setString(first, line.getSizeName());
        if (line.getSizePercent() == null) statement.setNull(first + 1, Types.INTEGER); else statement.setInt(first + 1, line.getSizePercent());
        statement.setString(first + 2, line.getExtras());
        if (line.getOptionCost() == null) statement.setNull(first + 3, Types.BIGINT); else statement.setLong(first + 3, Money.toDong(line.getOptionCost()));
        return first + 4;
    }

    /** Reads the four values by column name from a row that selected them. */
    static void read(ResultSet rs, OrderItem line) throws SQLException {
        line.setSizeName(rs.getString("size_name"));
        int percent = rs.getInt("size_percent");
        line.setSizePercent(rs.wasNull() ? null : percent);
        line.setExtras(rs.getString("extras"));
        long cost = rs.getLong("option_cost");
        line.setOptionCost(rs.wasNull() ? null : BigDecimal.valueOf(cost));
    }
}

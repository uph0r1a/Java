package com.coffeeshop.coffeeshopmanagement.service;

/**
 * Who may empty a table that has an unpaid order on it (TODO 3.8). Emptying cancels that order, so only an administrator or
 * the cashier who opened the order may do it; another cashier asks them (or an administrator).
 */
public final class TablePolicy {

    public static final String REFUSED_MESSAGE =
            "Đơn này do nhân viên khác mở. Chỉ nhân viên đã mở đơn hoặc quản trị viên mới được hủy đơn đang phục vụ.";

    private TablePolicy() {}

    /**
     * @param admin               the signed-in person is an administrator
     * @param currentEmployeeId   the signed-in cashier's employee id, or null
     * @param orderEmployeeId     the employee who opened the open order, or null when none was recorded
     */
    public static boolean canEmptyTableWithOpenOrder(boolean admin, Integer currentEmployeeId, Integer orderEmployeeId) {
        if (admin) return true;
        return currentEmployeeId != null && currentEmployeeId.equals(orderEmployeeId);
    }
}

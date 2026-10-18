package com.coffeeshop.coffeeshopmanagement.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Orders take their time from the till's own clock (TODO 3.10). A computer whose date is wrong puts sales on the wrong day
 * in every report. The one thing we can detect: the clock is BEHIND the newest order already saved, which can only mean the
 * date was set back (a dead clock battery, a wrong time zone). Then the start-up warns, once.
 */
public final class ClockCheck {

    /** A small difference is normal (another till's clock, a sync); more than this is a wrong date. */
    static final Duration TOLERANCE = Duration.ofMinutes(10);

    private ClockCheck() {}

    /** The warning to show, or null when the clock looks fine. {@code newestRecorded} may be null (no orders yet). */
    public static String problem(LocalDateTime now, LocalDateTime newestRecorded) {
        if (now == null || newestRecorded == null) return null;
        if (!now.plus(TOLERANCE).isBefore(newestRecorded)) return null;
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
        return "Đồng hồ của máy này đang chỉ " + now.format(f) + ", nhưng đã có đơn hàng ghi lúc "
                + newestRecorded.format(f) + ".\nNgày giờ máy có thể bị sai nên đơn mới sẽ được ghi vào sai ngày "
                + "và báo cáo sẽ lệch. Hãy chỉnh lại ngày giờ của máy rồi mở lại chương trình.";
    }
}

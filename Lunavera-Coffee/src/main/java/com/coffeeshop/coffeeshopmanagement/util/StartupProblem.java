package com.coffeeshop.coffeeshopmanagement.util;

import java.nio.file.Path;

/**
 * The plain-language text shown when the database cannot be opened at start-up (TODO 2.5). Before, the exception
 * left {@code Application.init()} and the window simply never appeared; the only trace was in the log.
 */
public final class StartupProblem {

    private StartupProblem() {
    }

    public static String title() {
        return "Không thể mở dữ liệu của quán";
    }

    /** What to show: what happened in one line, the likely causes, where the data and the log are, and what to do. */
    public static String describe(Throwable error) {
        return describe(error, safe(DataPaths::dataDir), safe(DataPaths::logsDir), safe(DataPaths::backupsDir))
                + serverHint();
    }

    /**
     * Extra lines when the shop uses a MariaDB/MySQL server: the most common reason it will not open is that the
     * server is not running or the settings in data/database.properties are wrong. Empty for SQLite (and if the
     * settings themselves cannot be read, the technical detail above already says why).
     */
    static String serverHint() {
        try {
            return serverHint(com.coffeeshop.coffeeshopmanagement.config.DatabaseSettings.load(),
                    com.coffeeshop.coffeeshopmanagement.config.DatabaseSettings.fileExists());
        } catch (RuntimeException e) {
            return "";
        }
    }

    static String serverHint(com.coffeeshop.coffeeshopmanagement.config.DatabaseSettings settings, boolean settingsFileExists) {
        if (!settings.isMariaDb()) {
            return "";
        }
        String hint = "\n\nCửa hàng đang dùng máy chủ cơ sở dữ liệu: " + settings.describe() + ".\n";
        if (!settingsFileExists) {
            hint += "Chưa có tệp " + DataPaths.dataDir().resolve("database.properties") + ". Hãy chép database.properties.example "
                    + "(trong thư mục dự án) vào đó rồi điền máy chủ, tên, tài khoản, mật khẩu (xem docs/DATABASE.md). "
                    + "Muốn dùng tệp SQLite thay cho máy chủ thì ghi db.type=sqlite.\n";
        }
        return hint + "Hãy kiểm tra: máy chủ MariaDB/MySQL (ví dụ XAMPP) đã được bật chưa, máy này có nối mạng tới máy chủ không, "
                + "và tên, tài khoản, mật khẩu trong " + DataPaths.dataDir().resolve("database.properties") + " có đúng không. "
                + "Không có máy chủ thì không bán hàng được; dữ liệu vẫn nằm an toàn trên máy chủ.";
    }

    static String describe(Throwable error, String dataDir, String logsDir, String backupsDir) {
        StringBuilder text = new StringBuilder();
        text.append("Lunavera Coffee không mở được cơ sở dữ liệu nên không thể khởi động. ")
                .append("Dữ liệu của bạn chưa bị xóa.\n\n");
        text.append("Lý do thường gặp: tệp dữ liệu đang được một chương trình khác (hoặc một cửa sổ Lunavera khác) mở, ")
                .append("ổ đĩa đầy, thư mục dữ liệu không ghi được, hoặc tệp bị hỏng.\n\n");
        text.append("Chi tiết kỹ thuật: ").append(rootMessage(error)).append("\n\n");
        text.append("Thư mục dữ liệu: ").append(dataDir).append('\n');
        text.append("Nhật ký lỗi: ").append(logsDir).append('\n');
        text.append("Bản sao lưu: ").append(backupsDir).append("\n\n");
        text.append("Cách xử lý: đóng các cửa sổ Lunavera khác và thử lại. Nếu vẫn lỗi, đừng xóa tệp lunavera.db: ")
                .append("hãy gửi tệp nhật ký cho người hỗ trợ, hoặc chép một bản sao lưu từ thư mục sao lưu ")
                .append("thay vào tệp lunavera.db (khi đã đóng chương trình).");
        return text.toString();
    }

    /** The deepest cause's message, or its class name when it has none. */
    static String rootMessage(Throwable error) {
        if (error == null) return "(không rõ)";
        Throwable root = error;
        int guard = 0;
        while (root.getCause() != null && root.getCause() != root && guard++ < 20) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return (message == null || message.isBlank()) ? root.getClass().getSimpleName() : message;
    }

    private static String safe(java.util.concurrent.Callable<Path> path) {
        try {
            return path.call().toAbsolutePath().toString();
        } catch (Exception e) {
            return "(không xác định được)";
        }
    }
}

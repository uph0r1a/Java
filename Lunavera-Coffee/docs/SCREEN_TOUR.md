# Screen tour: open every window once, in both languages

This is the check that TODO 6.67 leaves to a person: the program has been compiled and its tests run, but a screen, a dialog or a button can still fail only when it is opened (a missing text, a cut-off label, a dead button, an error box). The list below has every window of the program. `ScreenTourTest` fails when a window or a screen is added to the code and not to this list, or when the list names one that no longer exists, so the tour does not go out of date.

## How to do it

1. Use a **copy** of the shop data, or a fresh database with a few test products, one customer, one voucher and one gift card. Sign in as the owner for the owner rows and as a cashier (role Employee) for the cashier rows.
2. Go through the whole list once in Vietnamese, then switch the language (the language pill, `LanguagePill`) and go through it again in English. Do it once at a normal size and once at 1366 x 768 if you have such a screen.
3. For every row: the window opens with no error box; no text looks like a raw key (`pos.xxx`) or `???`; nothing is cut off; every button on it reacts; closing it (Esc or the X) leaves the screen behind it working.
4. Tick the row, or write what was wrong next to it. Send me the list.

## The ten screens (FXML)

| Done | Screen | Controller | Open it by | Who |
|---|---|---|---|---|
| [ ] | `dangnhap.fxml` | `LoginController` | Start the program, or **Đăng xuất** | Both |
| [ ] | `employee-trangchu.fxml` | `EmployeeController` | Sign in as a cashier | Cashier |
| [ ] | `admin-trangchu.fxml` | `AdminDashboardController` | Sign in as the owner | Owner |
| [ ] | `quanlydonhang.fxml` | `OrderController` | **Quản lý đơn hàng** (the till) | Both |
| [ ] | `quanlysanpham.fxml` | `ProductController` | **Quản lý sản phẩm** | Owner |
| [ ] | `quanlydanhmuc.fxml` | `CategoryController` | **Quản lý danh mục** | Owner |
| [ ] | `quanlytaikhoan.fxml` | `AccountController` | **Quản lý tài khoản** | Owner |
| [ ] | `quanlynhanvien.fxml` | `StaffController` | **Quản lý nhân viên** | Owner |
| [ ] | `quanlykhachhang.fxml` | `CustomerController` | **Quản lý khách hàng** | Both |
| [ ] | `donthanhtoan.fxml` | `HoaDonController` | Pay a test order, or **Xem hóa đơn** in the order history | Both |

## The windows, dialogs and panels

| Done | Class | Open it by | Who |
|---|---|---|---|
| [ ] | `FirstRunDialog` | Delete the `data/` folder (a copy!) and start the program: **Thiết lập lần đầu** | First start |
| [ ] | `SetupWizard` | Owner home: **Thiết lập ban đầu** | Owner |
| [ ] | `PasswordChangeDialog` | Sidebar **Đổi mật khẩu**; also forced after signing in with a default password | Both |
| [ ] | `LockScreen` | Sidebar **Khóa**, and after the idle time | Both |
| [ ] | `PinSetupDialog` | Sidebar **Mã PIN** | Cashier |
| [ ] | `TimeClockDialog` | Sidebar **Chấm công**; also the box offered at sign-in and sign-out | Both |
| [ ] | `TimeClockCorrectionDialog` | Inside **Chấm công** as the owner: **Sửa hoặc thêm giờ làm...** | Owner |
| [ ] | `ShiftCloseDialog` | Sidebar **Chốt ca** | Both |
| [ ] | `ExpenseDialog` | Sidebar **Chi phí**, and **Ghi chi từ két** inside **Chốt ca** | Owner / Both |
| [ ] | `GiftCardDialog` | Sidebar **Thẻ quà tặng** | Both |
| [ ] | `GiftCardListDialog` | Inside **Thẻ quà tặng** as the owner: the card list | Owner |
| [ ] | `VoucherDialog` | Sidebar **Mã giảm giá** | Owner |
| [ ] | `PromotionDialog` | Sidebar **Khuyến mãi tự động** | Owner |
| [ ] | `AuditLogWindow` | Sidebar **Nhật ký thao tác** | Owner |
| [ ] | `SettingsDialog` | Owner home: **Cài đặt** | Owner |
| [ ] | `StartSellingDialog` | Inside **Cài đặt**: **Bắt đầu bán thật...** (on a COPY of the data) | Owner |
| [ ] | `HelpDialog` | Inside **Cài đặt**: **Hướng dẫn cho chủ quán** / **Hướng dẫn cho thu ngân** (TODO 6.71) | Owner |
| [ ] | `TableManagerDialog` | Owner home: **Quản lý bàn**, or inside **Cài đặt** | Owner |
| [ ] | `TransferQrDialog` | Owner home: **Mã QR chuyển khoản** | Owner |
| [ ] | `BackupDialogs` | Owner home: **Sao lưu dữ liệu** and **Khôi phục dữ liệu**; and the question when signing out | Owner |
| [ ] | `BackupPassphraseDialog` | **Sao lưu dữ liệu** with a passphrase; **Khôi phục dữ liệu** of a `.lvbak` file | Owner |
| [ ] | `MariaDbRestoreDialogs` | **Khôi phục dữ liệu** on a MariaDB shop (a test server) | Owner |
| [ ] | `IngredientDialog` | Owner home: **Nguyên liệu** | Owner |
| [ ] | `RecipePane` | Inside **Nguyên liệu**: the recipes tab | Owner |
| [ ] | `OptionsPane` | Inside **Nguyên liệu**: sizes and toppings | Owner |
| [ ] | `OptionEditDialog` | Inside the sizes and toppings tab: add or edit one | Owner |
| [ ] | `StockLogDialog` | On **Quản lý sản phẩm**: **Nhập kho** | Owner |
| [ ] | `StockHistoryDialog` | In **Nhập kho** or **Nguyên liệu**: **Lịch sử** | Owner |
| [ ] | `CategoryDialog` | Add or edit a category (from the categories screen or from the products screen) | Owner |
| [ ] | `ReportWindow` | Owner home: **Báo cáo doanh thu** (look at every tab) | Owner |
| [ ] | `DiscountReportDialog` | Inside the report: the **Giảm giá** button | Owner |
| [ ] | `OrderHistoryWindow` | **Lịch sử đơn hàng** (till, customers, report) | Both |
| [ ] | `RefundDialog` | In the order history, owner: **Hủy đơn hàng** | Owner |
| [ ] | `RefundLinesDialog` | In the order history, owner: **Hoàn từng dòng** | Owner |
| [ ] | `CustomerPickerDialog` | At the till: **Chọn KH**; on the customers screen: merge duplicates | Both |
| [ ] | `OptionPickerDialog` | At the till: press a product that has sizes or toppings | Cashier |
| [ ] | `OrderNoteDialog` | At the till: the note of an order line | Cashier |
| [ ] | `PaymentDialog` | At the till: **✓ Thanh toán** (the payment box: cash, transfer, card, customer, discount; moved out of the till screen in TODO 6.54) | Cashier |
| [ ] | `PromotionPanel` | In the payment box, with an automatic promotion switched on | Cashier |
| [ ] | `SplitPaymentPanel` | In the payment box: **Trả bằng nhiều cách** | Cashier |
| [ ] | `TableMovePanel` | At the till: **Chuyển / Gộp / Tách** | Cashier |
| [ ] | `TakeAwayPanel` | At the till: **Đơn mới** on a take-away or delivery channel | Cashier |
| [ ] | `ReservationPanel` | At the till and on the owner home: **Đặt bàn** | Both |
| [ ] | `ReservationDialog` | Inside **Đặt bàn**: write a booking down | Both |
| [ ] | `ComplimentaryDialog` | At the till: the free-drink button (**Tặng nước miễn phí**) | Cashier |

## Also

- The message boxes (`AlertUtil`) and the other small questions (clock in, clock out, discard a basket, stale order, leftovers after sign-in) show when the thing happens; they are covered by the checks in section 0 of `TODO.md`, not by a row here.
- Keep the `Async.run(..., busy nodes)` calls in mind (TODO 6.67): while a payment or a save runs, the window must grey out and come back; if a button stays grey after an error, write it down.

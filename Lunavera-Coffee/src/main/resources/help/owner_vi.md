# Hướng dẫn cho chủ quán

Một trang cho người quản trị. Tên nút viết bằng chữ đậm, đúng như trên màn hình. Cách dùng quầy thu ngân nằm ở hướng dẫn cho nhân viên thu ngân.

## 1. Bắt đầu

- Lần đầu mở trên cơ sở dữ liệu mới, máy bắt tạo tài khoản chủ quán (tên, tên đăng nhập, mật khẩu). Không có mật khẩu có sẵn. **Thiết lập ban đầu** hỏi tên quán và số bàn; có thể **Để sau**.
- Chạy thử xong, trước ngày khai trương: **Cài đặt** → **Bắt đầu bán thật...** xóa đơn và ghi chép chạy thử, giữ thực đơn, bàn, nhân viên. Máy sao lưu trước khi xóa.

## 2. Người và tài khoản

- **Quản lý nhân viên**: hồ sơ (lương, chức vụ, ngày vào làm), kể cả người chưa có tài khoản. **Cho nghỉ việc** khi họ nghỉ.
- **Quản lý tài khoản**: tên đăng nhập, quyền, mật khẩu, **Khóa** tài khoản. Nhân viên chỉ thấy các màn hình bán hàng và khách hàng.
- Mỗi người tự đổi mật khẩu bằng **Đổi mật khẩu**; nhân viên có thể đặt **Mã PIN** để mở khóa máy nhanh.

## 3. Thực đơn và kho

- **Quản lý sản phẩm** và **Quản lý danh mục**. Bấm **Nhập kho** để ghi hàng nhập (giá vốn, nhà cung cấp) và xem nhật ký kho; **Ghi hao hụt** cho hàng hỏng, đổ, hết hạn.
- **Nguyên liệu** (**Nguyên liệu và công thức**): công thức trừ kho khi bán. Kích cỡ và topping đặt ở màn hình sản phẩm; báo cáo có tab **Kích cỡ và topping**.
- Sản phẩm sắp hết hàng báo trên trang chủ; mức báo chung đặt trong **Cài đặt**.

## 4. Giảm giá và khách quen

- **Mã giảm giá**: **Tạo mã** (phần trăm hoặc số tiền, đơn tối thiểu, số lượt, khoảng ngày, **Mỗi khách dùng một lần**). **Bật / tắt mã đang chọn** để dừng mã mà không xóa.
- **Khuyến mãi tự động**: giảm theo phần trăm, đồng giá, mua X tặng Y, giá combo, theo ngày và giờ (ví dụ giờ vàng).
- **Cài đặt** → **Quy tắc của cửa hàng**: mức giảm tối đa của nhân viên, cách tích và dùng điểm, ngưỡng hạng Bạc và Vàng, giảm giá sinh nhật.
- **Quản lý khách hàng**: chỉ chủ quán đổi số điện thoại, gộp khách trùng, ẩn danh hóa khách, xuất danh sách.

## 5. Thẻ quà tặng

**Thẻ quà tặng** → **Danh sách thẻ**: mã, số tiền, số dư, ai bán, ngày bán, dùng lần cuối, và tổng tiền quán còn nợ khách trên các thẻ. **Hủy thẻ đã chọn** chỉ với thẻ chưa dùng lần nào (trả lại tiền cho khách).

## 6. Bàn và đặt bàn

**Quản lý bàn** (hoặc **Cài đặt** → **Quản lý bàn...**): thêm, đổi tên, xóa bàn; bàn đang có đơn không xóa được. Đặt bàn cũ không ai đóng sẽ hiện nhắc: **Đóng các đặt bàn cũ**.

## 7. Giờ làm

**Chấm công** cho chủ quán hiện giờ làm của mọi người trong khoảng ngày và ai đang trong ca. Ca quên ra ca: **Chốt giờ ra bị quên** (gõ giờ về thật). Ca sai hoặc bị bỏ sót: **Sửa hoặc thêm giờ làm...** (bắt buộc ghi lý do). Tiền công trong bảng chỉ là ước tính.

## 8. Báo cáo và sổ sách

- **Báo cáo doanh thu**: theo ngày, sản phẩm, giờ, thứ, danh mục, nhân viên, cách thanh toán; so với kỳ trước; nút **Giảm giá** cho giảm giá theo người và lý do.
- **Sổ bán hàng**: xuất sổ doanh thu theo kỳ.
- **Chi phí**: chi phí và tiền lấy ra khỏi két. Tab **Nhân viên và chốt ca** của báo cáo cho doanh thu theo người thu tiền và các ca đã chốt với chênh lệch.
- **Lịch sử đơn hàng**: chỉ chủ quán **Hủy đơn hàng** hoặc **Hoàn từng dòng** (có thể trả hàng về kho), và xuất danh sách.

## 9. Sao lưu và an toàn dữ liệu

- **Sao lưu dữ liệu**: nên làm mỗi tuần và lưu ra ngoài ổ đĩa của máy (ổ USB, đám mây). Có thể đặt mật khẩu cho bản sao lưu.
- **Nơi sao lưu thứ hai**: chọn thêm một thư mục (USB, máy khác, thư mục đồng bộ) để các bản sao lưu tự động cũng được chép sang đó. **Khôi phục dữ liệu** đưa dữ liệu về một bản sao lưu: thử trên bản sao trước, và máy luôn giữ bản an toàn của dữ liệu hiện tại.
- **Mở phpMyAdmin** chỉ có khi dùng MariaDB.

## 10. Theo dõi

**Nhật ký thao tác** ghi ai làm gì: giảm giá, hoàn tiền, chuyển và gộp bàn, đổi giá, tài khoản và mật khẩu, thẻ quà tặng, sửa giờ làm, khôi phục dữ liệu, đổi quy tắc. **Nhật ký bảo mật** là các sự kiện bảo mật của máy, chỉ để xem. **Mã QR chuyển khoản** là ảnh QR hiện ở hộp thanh toán: đổi ảnh này là đổi nơi nhận tiền nên máy hỏi xác nhận.

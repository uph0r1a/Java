package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.QrCode;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptQr;

import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Backs the invoice/receipt popup (donthanhtoan.fxml). The window is opened by {@code OrderController} after a payment and by
 * {@code OrderHistoryWindow} for an old order; each loads the FXML through {@code SceneNavigator.loadAndShowNewWindow} and
 * then calls {@link #setInvoiceData}, which fills every label, the QR code and the cancelled banner. It also prints the
 * receipt ({@link #handlePrint}).
 */
public class HoaDonController {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(HoaDonController.class.getName());
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    @FXML
    private ScrollPane scroller;
    @FXML
    private VBox invoiceRoot;
    @FXML
    private Label staffLabel;
    @FXML
    private Label timeCaptionLabel;
    @FXML
    private Label statusBannerLabel;
    @FXML
    private Label discountReasonLabel;
    @FXML
    private Label invoiceIdLabel;
    @FXML
    private Label tableLabel;
    @FXML
    private Label customerLabel;
    @FXML
    private Label orderTimeLabel;
    @FXML
    private Label pointsLabel;
    @FXML
    private TableView<OrderItem> invoiceDetailTable;
    @FXML
    private TableColumn<OrderItem, String> productColumn;
    @FXML
    private TableColumn<OrderItem, Number> quantityColumn;
    @FXML
    private TableColumn<OrderItem, String> priceColumn;
    @FXML
    private TableColumn<OrderItem, String> subtotalColumn;
    @FXML
    private Label subtotalLabel;
    @FXML
    private Label discountLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label paymentMethodLabel;
    @FXML
    private ImageView qrCodeImage;
    @FXML
    private Label qrOrderIdLabel;
    @FXML
    private Button closeButton;

    @FXML
    private void initialize() {
        productColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(ReceiptText.productName(data.getValue().getProductName())));
        quantityColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(data.getValue().getQuantity()));
        priceColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(CurrencyUtil.format(data.getValue().getUnitPrice())));
        subtotalColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(CurrencyUtil.format(data.getValue().getLineTotal())));
    }

    private Order shownOrder;
    private List<OrderItem> shownItems = List.of();
    private String shownEmployee;
    private String shownCustomer;
    /** The customer's total points, read after the window opens (TODO 5.28); null until then or when it could not be read. */
    private Integer shownPointsBalance;

    /** Populates every field on the receipt from a completed order. */
    public void setInvoiceData(Order order, List<OrderItem> items, String employeeName, String customerName) {
        shownOrder = order;
        shownItems = List.copyOf(items);
        shownEmployee = employeeName;
        shownCustomer = customerName;
        shownPointsBalance = null;
        invoiceIdLabel.setText("#" + order.getId());
        showPoints(order);
        staffLabel.setText(employeeName != null ? employeeName : "-");
        tableLabel.setText(ReceiptText.table(order.getTableNumber()));
        customerLabel.setText(customerName != null ? customerName : ReceiptText.t("receipt.guest"));
        // The time of payment, not of opening (TODO 2.44); the opening time is shown only when it is much earlier.
        java.time.LocalDateTime shown = order.getPaidAt() != null ? order.getPaidAt() : order.getOrderDate();
        orderTimeLabel.setText(shown != null ? shown.format(TIME_FORMAT) : "-");
        boolean cancelled = order.getStatus() == OrderStatus.CANCELLED;
        // A refunded order must not pass for proof of a sale (TODO 2.45).
        boolean paid = order.getStatus() == OrderStatus.PAID;
        statusBannerLabel.setText(ReceiptText.t(cancelled ? "receipt.cancelled" : "receipt.paid"));
        statusBannerLabel.setStyle(paid ? "-fx-text-fill: #2e7d32; -fx-background-color: #e8f5e9; -fx-border-color: #2e7d32; -fx-border-width: 1; -fx-border-radius: 4;" : "");
        statusBannerLabel.setVisible(cancelled || paid);
        statusBannerLabel.setManaged(cancelled || paid);

        invoiceDetailTable.getItems().setAll(items);
        // The item table grows with the order instead of scrolling inside a fixed box (TODO 2.22).
        double tableHeight = Math.max(2, items.size()) * invoiceDetailTable.getFixedCellSize() + 34;
        invoiceDetailTable.setPrefHeight(tableHeight);
        invoiceDetailTable.setMinHeight(tableHeight);
        invoiceDetailTable.setMaxHeight(tableHeight);
        fitWindowToContent();

        subtotalLabel.setText(CurrencyUtil.format(order.getSubtotal()));
        discountLabel.setText(CurrencyUtil.format(order.getDiscount()));
        boolean hasReason = order.getDiscountReason() != null && !order.getDiscountReason().isBlank();
        discountReasonLabel.setText(hasReason ? ReceiptText.t("receipt.reason", order.getDiscountReason()) : "");
        discountReasonLabel.setVisible(hasReason);
        discountReasonLabel.setManaged(hasReason);
        totalLabel.setText(CurrencyUtil.format(order.getTotal()));
        PaymentMethod method = order.getPaymentMethod();
        // Only the method's name: the transfer QR picture is never printed on a receipt, because a
        // paid receipt that still shows a payment QR could be scanned and paid a second time.
        java.util.List<String> extras = ReceiptText.paymentExtras(order); // cash received and change, or the transfer reference
        paymentMethodLabel.setWrapText(true);
        paymentMethodLabel.setText(extras.isEmpty() ? ReceiptText.paymentMethod(method)
                : ReceiptText.paymentMethod(method) + "\n" + String.join("\n", extras));

        qrOrderIdLabel.setText("#" + order.getId());
        showQrCode(order);
    }

    /**
     * "Points earned: +N, total: M" under the customer line, only for a paid order with a customer (TODO 5.28). The earned part is
     * known at once; the balance is read in the background, so the window never waits for the database.
     */
    private void showPoints(Order order) {
        Integer earned = com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy.pointsEarnedBy(order);
        boolean show = earned != null;
        pointsLabel.setVisible(show);
        pointsLabel.setManaged(show);
        pointsLabel.setText(show ? ReceiptText.points(earned, null) : "");
        if (!show) return;
        int customerId = order.getCustomerId();
        com.coffeeshop.coffeeshopmanagement.util.Async.run(
                () -> new com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO().findById(customerId)
                        .map(com.coffeeshop.coffeeshopmanagement.model.Customer::getLoyaltyPoints).orElse(null),
                balance -> {
                    if (shownOrder != order || balance == null) return; // another receipt was loaded meanwhile
                    shownPointsBalance = balance;
                    pointsLabel.setText(ReceiptText.points(earned, balance));
                },
                error -> LOGGER.log(java.util.logging.Level.INFO, "Could not read the customer's points for the receipt", error));
    }

    private void showQrCode(Order order) {
        if (qrCodeImage == null) return;
        try {
            qrCodeImage.setImage(renderQr(QrCode.encodeText(ReceiptQr.payload(order))));
            qrCodeImage.setSmooth(false); // keep module edges crisp when scaled to the slot
        } catch (RuntimeException e) {
            // a receipt without a QR is better than no receipt, but leave a trace so a broken QR can be diagnosed (TODO 2.27)
            LOGGER.log(java.util.logging.Level.WARNING, "Could not draw the receipt QR for order " + order.getId(), e);
            qrCodeImage.setImage(null);
        }
    }

    /** Paints the modules as black squares on white with the 4-module quiet zone scanners need. */
    private static WritableImage renderQr(QrCode qr) {
        final int quiet = 4;
        int modules = qr.getSize() + 2 * quiet;
        int px = Math.max(3, 270 / modules);
        WritableImage image = new WritableImage(modules * px, modules * px);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < modules * px; y++) {
            for (int x = 0; x < modules * px; x++) {
                boolean dark = qr.getModule(x / px - quiet, y / px - quiet);
                writer.setColor(x, y, dark ? Color.BLACK : Color.WHITE);
            }
        }
        return image;
    }

    /** Sizes the window to the receipt, at most 90% of the screen height; longer receipts scroll on screen. */
    private void fitWindowToContent() {
        double wanted = invoiceRoot.prefHeight(520) + 4;
        double screen = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight();
        scroller.setPrefHeight(Math.min(wanted, screen * 0.9));
        scroller.setVvalue(0);
        javafx.application.Platform.runLater(() -> {
            if (scroller.getScene() != null && scroller.getScene().getWindow() instanceof Stage stage) {
                stage.sizeToScene();
                stage.centerOnScreen();
                javafx.application.Platform.runLater(() -> scroller.setVvalue(0)); // after layout: start at the top
            }
        });
    }

    @FXML
    public void handlePrint() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            AlertUtil.error(I18n.t("receipt.print.fail.title"), I18n.t("receipt.print.noprinter"));
            return;
        }
        boolean proceed = job.showPrintDialog(invoiceRoot.getScene().getWindow());
        if (!proceed) {
            job.cancelJob(); // the person closed the print dialog: do not leave the job open (TODO 2.14)
            return;
        }
        // Roll paper (58 / 80 mm): print the narrow layout built for that width instead of shrinking the screen receipt (TODO 4.3).
        javafx.scene.Node toPrint = invoiceRoot;
        double printableWidth = job.getJobSettings().getPageLayout().getPrintableWidth();
        if (shownOrder != null && com.coffeeshop.coffeeshopmanagement.util.ThermalReceipt.isNarrow(printableWidth)) {
            try {
                javafx.print.Printer printer = job.getPrinter();
                javafx.print.PageLayout wide = printer.createPageLayout(job.getJobSettings().getPageLayout().getPaper(),
                        javafx.print.PageOrientation.PORTRAIT, javafx.print.Printer.MarginType.HARDWARE_MINIMUM);
                job.getJobSettings().setPageLayout(wide);
                printableWidth = wide.getPrintableWidth();
            } catch (RuntimeException e) {
                LOGGER.log(java.util.logging.Level.INFO, "Could not use the smallest margins for the roll", e);
            }
            javafx.scene.image.Image qr = qrCodeImage == null ? null : qrCodeImage.getImage();
            javafx.scene.layout.VBox narrow = com.coffeeshop.coffeeshopmanagement.util.ThermalReceipt.build(
                    shownOrder, shownItems, shownEmployee, shownCustomer, qr, printableWidth, shownPointsBalance);
            com.coffeeshop.coffeeshopmanagement.util.ThermalReceipt.layOut(narrow);
            toPrint = narrow;
        }
        // The whole receipt is printed on one page: shrunk to fit when the page is smaller (a long order, a narrow roll).
        javafx.print.PageLayout layout = job.getJobSettings().getPageLayout();
        double scale = Math.min(1.0, Math.min(layout.getPrintableWidth() / toPrint.getLayoutBounds().getWidth(),
                layout.getPrintableHeight() / toPrint.getLayoutBounds().getHeight()));
        javafx.scene.transform.Scale transform = new javafx.scene.transform.Scale(scale, scale);
        if (scale < 1.0) {
            toPrint.getTransforms().add(transform);
        }
        boolean printed;
        String driverProblem = null;
        try {
            printed = job.printPage(toPrint);
        } catch (RuntimeException e) {
            // A printer that went offline or a network printer that timed out: close the job and say why (TODO 2.83).
            LOGGER.log(java.util.logging.Level.WARNING, "The printer driver failed", e);
            printed = false;
            driverProblem = com.coffeeshop.coffeeshopmanagement.util.ErrorText.forScreen(e.getMessage());
        } finally {
            toPrint.getTransforms().remove(transform);
        }
        if (printed) {
            job.endJob();
            AlertUtil.info(I18n.t("receipt.print.title"), I18n.t("receipt.print.sent"));
        } else {
                try {
                    job.cancelJob(); // a failed page must not leave the job (and the printer queue entry) open
                } catch (RuntimeException e) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Could not cancel the print job", e);
                }
                AlertUtil.error(I18n.t("receipt.print.fail.title"), driverProblem == null
                        ? I18n.t("receipt.print.fail") : I18n.t("receipt.print.fail.driver", driverProblem));
        }
    }

    @FXML
    public void handleClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}

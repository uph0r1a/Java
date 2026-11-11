package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.PrintRunner;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptPrinting;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
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
    private Label shopNameLabel;
    @FXML
    private Label footerShopNameLabel;
    @FXML
    private Label shopDetailsLabel;
    @FXML
    private Label staffLabel;
    @FXML
    private Label timeCaptionLabel;
    @FXML
    private Label statusBannerLabel;
    /** "COPY - print no. 2": shown (and so printed) when the receipt was printed before (TODO 3.72). */
    @FXML
    private Label copyLabel;
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
        // The shop's own name (TODO 4.15), in capitals as the receipt always had it.
        String shop = com.coffeeshop.coffeeshopmanagement.config.ShopSettings.cachedName().toUpperCase(java.util.Locale.ROOT);
        shopNameLabel.setText(shop);
        footerShopNameLabel.setText(shop);
        // Address and phone (TODO 4.7): shown under the name only when the owner has typed them.
        java.util.List<String> details = ReceiptText.shopDetailLines();
        shopDetailsLabel.setText(String.join("\n", details));
        shopDetailsLabel.setVisible(!details.isEmpty());
        shopDetailsLabel.setManaged(!details.isEmpty());
        productColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(ReceiptText.withPromo(com.coffeeshop.coffeeshopmanagement.service.LineOptions.withOptions(
                        ReceiptText.productName(data.getValue().getProductName()), data.getValue()), data.getValue()))); // TODO 4.9, 4.47, 4.55
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
        tableLabel.setText(ReceiptText.tableOf(order));
        customerLabel.setText(customerName != null ? customerName : ReceiptText.t("receipt.guest"));
        // The time of payment, not of opening (TODO 2.44); the opening time is shown only when it is much earlier.
        orderTimeLabel.setText(ReceiptText.timeOf(order, TIME_FORMAT));
        // A refunded order must not pass for proof of a sale (TODO 2.45); an open order has no banner.
        String bannerKey = ReceiptText.bannerKey(order);
        boolean paid = order.getStatus() == OrderStatus.PAID;
        if (bannerKey != null) statusBannerLabel.setText(ReceiptText.t(bannerKey));
        statusBannerLabel.setStyle(paid ? "-fx-text-fill: #2e7d32; -fx-background-color: #e8f5e9; -fx-border-color: #2e7d32; -fx-border-width: 1; -fx-border-radius: 4;" : "");
        statusBannerLabel.setVisible(bannerKey != null);
        statusBannerLabel.setManaged(bannerKey != null);
        showCopyMark();

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
        // Only the method's name: the transfer QR picture is never printed on a receipt, because a
        // paid receipt that still shows a payment QR could be scanned and paid a second time.
        showPaymentText(order);
        loadGiftCardUses(order);

        qrOrderIdLabel.setText("#" + order.getId());
        showQrCode(order);
        showPrintOptionsButton();
    }

    /** The method(s) and the extra lines under them: cash received and change, the transfer reference, the gift cards (TODO 5.51). */
    private void showPaymentText(Order order) {
        java.util.List<String> extras = ReceiptText.paymentExtras(order);
        paymentMethodLabel.setWrapText(true);
        paymentMethodLabel.setText(extras.isEmpty() ? ReceiptText.paymentOf(order)
                : ReceiptText.paymentOf(order) + "\n" + String.join("\n", extras));
    }

    /** Reads which gift card paid and what is left on it, in the background, and adds it under the payment (TODO 5.51). */
    private void loadGiftCardUses(Order order) {
        if (!order.paidWithGiftCard() || !order.getGiftCardUses().isEmpty()) return;
        int orderId = order.getId();
        com.coffeeshop.coffeeshopmanagement.util.Async.run(
                () -> new com.coffeeshop.coffeeshopmanagement.service.GiftCardService().usesForOrder(orderId),
                uses -> {
                    if (shownOrder != order || uses.isEmpty()) return; // another receipt was loaded meanwhile
                    order.setGiftCardUses(uses);
                    showPaymentText(order);
                },
                error -> LOGGER.log(java.util.logging.Level.INFO, "Could not read the gift card of the receipt", error));
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
                () -> new com.coffeeshop.coffeeshopmanagement.service.CustomerService().findById(customerId)
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

    /** Shows the copy mark when the next print is not the first; hides it otherwise (TODO 3.72). */
    private void showCopyMark() {
        String mark = shownOrder == null ? null : ReceiptText.copyMark(shownOrder);
        copyLabel.setText(mark == null ? "" : mark);
        copyLabel.setVisible(mark != null);
        copyLabel.setManaged(mark != null);
    }

    /** After a page was printed: counts it (and notes a reprint in the audit log) off the screen thread, then updates the mark for the next one. */
    private void recordPrint(Order order) {
        com.coffeeshop.coffeeshopmanagement.util.Async.run(
                () -> new com.coffeeshop.coffeeshopmanagement.service.ReceiptPrintService().recordPrint(order.getId()),
                count -> {
                    if (count > 0) order.setPrintCount(count);
                    if (order == shownOrder) {
                        showCopyMark();
                        fitWindowToContent();
                    }
                },
                error -> AlertUtil.error(I18n.t("receipt.print.fail.title"), I18n.t("receipt.print.record.fail", error.getMessage())));
    }
    @FXML private javafx.scene.control.Button printButton;
    /** "Print with options": shown only when this computer prints without the dialog, so the dialog is still one click away (TODO 5.39). */
    @FXML private javafx.scene.control.Button printOptionsButton;

    /** The Print button: where the receipt goes follows what this computer chose (TODO 5.39); the dialog when nothing was chosen. */
    @FXML
    public void handlePrint() {
        print(false);
    }

    /** "Print with options": the print dialog, whatever this computer chose. */
    @FXML
    public void handlePrintWithOptions() {
        print(true);
    }

    /** Called by the till right after a payment: prints at once when this computer is set to print without the dialog and automatically (TODO 5.39). */
    public void printAfterPayment() {
        ReceiptPrinting.Choice choice = ReceiptPrinting.load();
        if (choice.direct() && choice.automatic()) print(false);
    }

    private void showPrintOptionsButton() {
        boolean direct = ReceiptPrinting.load().direct();
        if (printOptionsButton != null) {
            printOptionsButton.setVisible(direct);
            printOptionsButton.setManaged(direct);
        }
    }

    private void print(boolean forceDialog) {
        ReceiptPrinting.Choice choice = ReceiptPrinting.load();
        ReceiptPrinterJob.Opened opened = ReceiptPrinterJob.open(choice, forceDialog);
        if (opened == null) return; // the computer has no printer at all: the box said so
        PrinterJob job = opened.job();
        if (opened.showDialog()) {
            boolean proceed = job.showPrintDialog(invoiceRoot.getScene().getWindow());
            if (!proceed) {
                job.cancelJob(); // the person closed the print dialog: do not leave the job open (TODO 2.14)
                return;
            }
        }
        // Roll paper (58 / 80 mm): print the narrow layout built for that width instead of shrinking the screen receipt (TODO 4.3).
        javafx.scene.Node toPrint = invoiceRoot;
        double printableWidth = job.getJobSettings().getPageLayout().getPrintableWidth();
        if (shownOrder != null && ReceiptPrinting.useRoll(choice.paper(), printableWidth, com.coffeeshop.coffeeshopmanagement.util.ThermalReceipt.NARROW_BELOW_POINTS)) {
            try {
                javafx.print.Printer printer = job.getPrinter();
                javafx.print.PageLayout wide = printer.createPageLayout(job.getJobSettings().getPageLayout().getPaper(),
                        javafx.print.PageOrientation.PORTRAIT, javafx.print.Printer.MarginType.HARDWARE_MINIMUM);
                job.getJobSettings().setPageLayout(wide);
                printableWidth = wide.getPrintableWidth();
            } catch (RuntimeException e) {
                LOGGER.log(java.util.logging.Level.INFO, "Could not use the smallest margins for the roll", e);
            }
            double rollWidth = ReceiptPrinting.rollWidth(choice.paper(), printableWidth);
            javafx.scene.image.Image qr = qrCodeImage == null ? null : qrCodeImage.getImage();
            javafx.scene.layout.VBox narrow = com.coffeeshop.coffeeshopmanagement.util.ThermalReceipt.build(
                    shownOrder, shownItems, shownEmployee, shownCustomer, qr, rollWidth, shownPointsBalance);
            com.coffeeshop.coffeeshopmanagement.util.ThermalReceipt.layOut(narrow);
            toPrint = narrow;
        }
        // The whole receipt is printed on one page: shrunk to fit when the page is smaller (a long order, a narrow roll).
        javafx.print.PageLayout layout = job.getJobSettings().getPageLayout();
        double scale = Math.min(1.0, Math.min(layout.getPrintableWidth() / toPrint.getLayoutBounds().getWidth(),
                layout.getPrintableHeight() / toPrint.getLayoutBounds().getHeight()));
        final javafx.scene.Node page = ReceiptPrinterJob.offScreenPage(toPrint, scale);
        if (page == null) {
            // The receipt on the screen could not be copied: print it here, on the screen thread, as before.
            javafx.scene.transform.Scale transform = new javafx.scene.transform.Scale(scale, scale);
            if (scale < 1.0) toPrint.getTransforms().add(transform);
            try {
                showPrintOutcome(PrintRunner.run(ReceiptPrinterJob.jobOf(job, toPrint)));
            } finally {
                toPrint.getTransforms().remove(transform);
            }
            return;
        }
        // Printing a node that is not in a window may be done off the screen thread (TODO 2.83): a slow or offline network printer no
        // longer freezes the till while it answers; the button is back, and the result shown, when it has.
        if (printButton != null) printButton.setDisable(true);
        Async.run(() -> PrintRunner.run(ReceiptPrinterJob.jobOf(job, page)), outcome -> {
            if (printButton != null) printButton.setDisable(false);
            showPrintOutcome(outcome);
        }, error -> {
            if (printButton != null) printButton.setDisable(false);
            LOGGER.log(java.util.logging.Level.WARNING, "Printing failed", error);
            showPrintOutcome(new PrintRunner.Outcome(false, com.coffeeshop.coffeeshopmanagement.util.ErrorText.forScreen(error.getMessage())));
        });
    }

    private void showPrintOutcome(PrintRunner.Outcome outcome) {
        if (outcome.printed()) {
            if (shownOrder != null) recordPrint(shownOrder); // the copy mark for the next print (TODO 3.72)
            AlertUtil.info(I18n.t("receipt.print.title"), I18n.t("receipt.print.sent"));
        } else {
            AlertUtil.error(I18n.t("receipt.print.fail.title"), outcome.problem() == null
                    ? I18n.t("receipt.print.fail") : I18n.t("receipt.print.fail.driver", outcome.problem()));
        }
    }

    @FXML
    public void handleClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}

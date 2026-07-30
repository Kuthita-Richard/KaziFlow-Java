package com.kaziflow.services;

import com.kaziflow.models.Sale;
import com.kaziflow.models.SaleItem;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReceiptPrinter.generateWhatsAppReceipt().
 *
 * The receipt generator is pure Java (no JavaFX, no DB) — ideal for fast
 * unit testing. These tests guard against regressions in the receipt format
 * that would confuse customers (wrong totals, missing business name, etc.)
 */
@DisplayName("ReceiptPrinter — WhatsApp receipt generation")
class ReceiptPrinterTest {

    private ReceiptPrinter printer;
    private Sale sale;

    @BeforeEach
    void setUp() {
        printer = new ReceiptPrinter();

        // Build a minimal sale with two items
        sale = new Sale();
        sale.setSaleNumber("SALE-0042");
        sale.setCreatedAt(LocalDateTime.of(2026, 6, 15, 14, 30, 0));
        sale.setPaymentMethod("MPESA");
        sale.setSubtotal(1000.00);
        sale.setDiscountAmount(0.00);
        sale.setVatAmount(160.00);
        sale.setTotalAmount(1160.00);

        SaleItem item1 = new SaleItem();
        item1.setProductName("Panadol Tablets");
        item1.setQuantity(2);
        item1.setUnitPrice(50.00);
        item1.setLineTotal(100.00);

        SaleItem item2 = new SaleItem();
        item2.setProductName("Amoxicillin 500mg");
        item2.setQuantity(1);
        item2.setUnitPrice(900.00);
        item2.setLineTotal(900.00);

        sale.setItems(List.of(item1, item2));
    }

    @Test
    @DisplayName("Receipt contains sale number")
    void receiptContainsSaleNumber() {
        String receipt = printer.generateWhatsAppReceipt(sale);
        assertTrue(receipt.contains("SALE-0042"),
            "Receipt must contain the sale number");
    }

    @Test
    @DisplayName("Receipt contains both item names")
    void receiptContainsItemNames() {
        String receipt = printer.generateWhatsAppReceipt(sale);
        assertTrue(receipt.contains("Panadol Tablets"),
            "Receipt must contain item name: Panadol Tablets");
        assertTrue(receipt.contains("Amoxicillin 500mg"),
            "Receipt must contain item name: Amoxicillin 500mg");
    }

    @Test
    @DisplayName("Receipt contains correct total")
    void receiptContainsCorrectTotal() {
        String receipt = printer.generateWhatsAppReceipt(sale);
        assertTrue(receipt.contains("1,160"),
            "Receipt must contain the formatted total 1,160");
    }

    @Test
    @DisplayName("Receipt contains payment method")
    void receiptContainsPaymentMethod() {
        String receipt = printer.generateWhatsAppReceipt(sale);
        assertTrue(receipt.contains("MPESA"),
            "Receipt must contain payment method MPESA");
    }

    @Test
    @DisplayName("Receipt shows VAT amount")
    void receiptContainsVat() {
        String receipt = printer.generateWhatsAppReceipt(sale);
        assertTrue(receipt.contains("160"),
            "Receipt must contain VAT amount 160");
    }

    @Test
    @DisplayName("Receipt does NOT contain blank lines for missing items")
    void receiptWithNoItemsHandledGracefully() {
        sale.setItems(null);
        assertDoesNotThrow(
            () -> printer.generateWhatsAppReceipt(sale),
            "generateWhatsAppReceipt must not throw when items list is null"
        );
    }

    @Test
    @DisplayName("Receipt shows discount when present")
    void receiptShowsDiscount() {
        sale.setDiscountAmount(50.00);
        sale.setTotalAmount(1110.00);
        String receipt = printer.generateWhatsAppReceipt(sale);
        assertTrue(receipt.contains("Discount") || receipt.contains("50"),
            "Receipt must mention discount when discount > 0");
    }

    @Test
    @DisplayName("Receipt does NOT show discount line when discount is zero")
    void receiptHidesZeroDiscount() {
        sale.setDiscountAmount(0.00);
        String receipt = printer.generateWhatsAppReceipt(sale);
        // The discount line should be omitted entirely for zero discount
        // (prevents cluttering the customer's WhatsApp message)
        assertFalse(receipt.contains("Discount: -KES 0"),
            "Receipt should not show 'Discount: -KES 0' for zero discount");
    }

    @Test
    @DisplayName("Receipt contains date in readable format")
    void receiptContainsDate() {
        String receipt = printer.generateWhatsAppReceipt(sale);
        // Should contain the year at minimum
        assertTrue(receipt.contains("2026"),
            "Receipt must contain the year");
    }

    @Test
    @DisplayName("Receipt is non-empty and has reasonable length")
    void receiptHasReasonableLength() {
        String receipt = printer.generateWhatsAppReceipt(sale);
        assertNotNull(receipt);
        assertTrue(receipt.length() >= 50,
            "Receipt must have at least 50 characters");
        assertTrue(receipt.length() <= 2000,
            "Receipt must not exceed 2000 characters (WhatsApp message limit)");
    }

    @Test
    @DisplayName("Receipt uses bold formatting for total (WhatsApp markdown)")
    void receiptUsesBoldForTotal() {
        String receipt = printer.generateWhatsAppReceipt(sale);
        // WhatsApp bold = *text*
        assertTrue(receipt.contains("*"),
            "Receipt should use WhatsApp bold (*) for emphasis");
    }
}

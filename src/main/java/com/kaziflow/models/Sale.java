package com.kaziflow.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Sale {
    private int id;
    private String saleNumber;
    private Integer customerId;
    private String customerName;
    private double subtotal;
    private double discountAmount;
    private double deliveryCharge;
    private double vatAmount;
    private double totalAmount;
    private double amountPaid;
    private double changeAmount;
    private String paymentMethod;
    private String mpesaRef;
    private String status;
    private int servedBy;
    private String servedByName;
    private LocalDateTime createdAt;
    private List<SaleItem> items = new ArrayList<>();

    public Sale() {}

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getSaleNumber() { return saleNumber; }
    public void setSaleNumber(String saleNumber) { this.saleNumber = saleNumber; }
    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }
    public double getDeliveryCharge() { return deliveryCharge; }
    public void setDeliveryCharge(double deliveryCharge) { this.deliveryCharge = deliveryCharge; }
    public double getVatAmount() { return vatAmount; }
    public void setVatAmount(double vatAmount) { this.vatAmount = vatAmount; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(double amountPaid) { this.amountPaid = amountPaid; }
    public double getChangeAmount() { return changeAmount; }
    public void setChangeAmount(double changeAmount) { this.changeAmount = changeAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getMpesaRef() { return mpesaRef; }
    public void setMpesaRef(String mpesaRef) { this.mpesaRef = mpesaRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getServedBy() { return servedBy; }
    public void setServedBy(int servedBy) { this.servedBy = servedBy; }
    public String getServedByName() { return servedByName; }
    public void setServedByName(String servedByName) { this.servedByName = servedByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<SaleItem> getItems() { return items; }
    public void setItems(List<SaleItem> items) { this.items = items; }
}

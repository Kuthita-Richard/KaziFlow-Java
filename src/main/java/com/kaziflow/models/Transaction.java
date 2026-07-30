package com.kaziflow.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Transaction {
    private int id;
    private String reference;
    private String description;
    private int accountId;
    private String accountName;
    private String transactionType; // income, expense, transfer
    private String category;
    private double amount;
    private double vatAmount;
    private String paymentMethod;
    private String notes;
    private Integer relatedSaleId;
    private Integer relatedPurchaseId;
    private int createdBy;
    private LocalDate transactionDate;
    private LocalDateTime createdAt;

    public Transaction() {}

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public double getVatAmount() { return vatAmount; }
    public void setVatAmount(double vatAmount) { this.vatAmount = vatAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Integer getRelatedSaleId() { return relatedSaleId; }
    public void setRelatedSaleId(Integer relatedSaleId) { this.relatedSaleId = relatedSaleId; }
    public Integer getRelatedPurchaseId() { return relatedPurchaseId; }
    public void setRelatedPurchaseId(Integer relatedPurchaseId) { this.relatedPurchaseId = relatedPurchaseId; }
    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

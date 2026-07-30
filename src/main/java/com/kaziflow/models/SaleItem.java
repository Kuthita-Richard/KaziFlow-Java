package com.kaziflow.models;

public class SaleItem {
    private int id;
    private int saleId;
    private int productId;
    private String productName;
    private double quantity;
    private double unitPrice;
    private double costPrice;
    private double discount;
    private double lineTotal;

    public SaleItem() {}

    public SaleItem(int productId, String productName, double quantity, double unitPrice, double costPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.costPrice = costPrice;
        this.lineTotal = quantity * unitPrice;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSaleId() { return saleId; }
    public void setSaleId(int saleId) { this.saleId = saleId; }
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; recalculate(); }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; recalculate(); }
    public double getCostPrice() { return costPrice; }
    public void setCostPrice(double costPrice) { this.costPrice = costPrice; }
    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; recalculate(); }
    public double getLineTotal() { return lineTotal; }
    public void setLineTotal(double lineTotal) { this.lineTotal = lineTotal; }

    private void recalculate() {
        this.lineTotal = (quantity * unitPrice) - discount;
    }
}

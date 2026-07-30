package com.kaziflow.models;

public class PurchaseItem {
    private int id;
    private int purchaseId;
    private int productId;
    private String productName;
    private String sku;
    private double quantity;
    private double unitCost;
    private double lineTotal;

    public PurchaseItem() {}

    public PurchaseItem(int productId, String productName, double quantity, double unitCost) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.lineTotal = quantity * unitCost;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getPurchaseId() { return purchaseId; }
    public void setPurchaseId(int purchaseId) { this.purchaseId = purchaseId; }
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; this.lineTotal = quantity * unitCost; }
    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost = unitCost; this.lineTotal = quantity * unitCost; }
    public double getLineTotal() { return lineTotal; }
    public void setLineTotal(double lineTotal) { this.lineTotal = lineTotal; }
}

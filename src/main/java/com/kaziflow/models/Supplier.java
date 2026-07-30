package com.kaziflow.models;

public class Supplier {
    private int id;
    private String code;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String category;
    private int paymentTerms;
    private double outstandingBalance;
    private String paymentStatus;
    private String status;
    private String notes;

    public Supplier() {}

    public Supplier(String name, String phone, String email, String category) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.category = category;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(int paymentTerms) { this.paymentTerms = paymentTerms; }
    public double getOutstandingBalance() { return outstandingBalance; }
    public void setOutstandingBalance(double outstandingBalance) { this.outstandingBalance = outstandingBalance; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() { return name; }
}

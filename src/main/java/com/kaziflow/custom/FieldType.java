package com.kaziflow.custom;

/** The kinds of field an administrator can give an entity in a custom module. */
public enum FieldType {
    TEXT("Text"), LONG_TEXT("Long text"), INTEGER("Whole number"), DECIMAL("Decimal number"), MONEY("Money (KES)"),
    DATE("Date"), DATETIME("Date & time"), BOOLEAN("Yes / No"), CHOICE("Choice from a list"),
    LOOKUP("Link to another record"), CALC("Calculated");

    private final String label;
    FieldType(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }

    public boolean isNumeric() { return this == INTEGER || this == DECIMAL || this == MONEY; }
    /** Types a formula may read with {f_n}. */
    public boolean usableInFormula() { return isNumeric() || this == CALC; }
}

package com.kaziflow.database;

import com.kaziflow.dao.*;

/**
 * Everything the application needs beyond {@link DatabaseManager}'s base schema: the optional-module tables and the columns
 * added after the first release. Idempotent; {@code App.start()} runs it at startup and so do the verification suites, so a
 * database that never went through the application's start-up (a test, a tool) has the same schema as one that did — before
 * this existed, {@code sales.delivery_charge} and the audit-log hash columns only appeared when {@code App.start()} ran.
 */
public final class SchemaMigrations {
    private SchemaMigrations() {}

    public static void runAll() {
        new DeniDAO().ensureTables();
        new QuotationDAO().ensureTables();
        new PurchaseReturnDAO().ensureTables();
        new PayrollDAO().ensureTables();
        new WorkshopDAO().ensureTables();
        new GymDAO().ensureTables();
        new LaundryDAO().ensureTables();
        new HotelDAO().ensureTables();
        new FuelStationDAO().ensureTables();
        new SchoolCanteenDAO().ensureTables();
        new AppointmentDAO().ensureTables();
        new PatientDAO().ensureTables();
        new RestaurantDAO().ensureTables();
        new SaccoDAO().ensureTables();
        new DeliveryDAO().ensureTables();
        new StockTakeDAO().ensureTables();
        new CustomerDAO().ensureLoyaltyColumn();
        com.kaziflow.services.AuditLog.ensureHashColumns();
        com.kaziflow.services.NumberSequence.ensureTable();
    }
}

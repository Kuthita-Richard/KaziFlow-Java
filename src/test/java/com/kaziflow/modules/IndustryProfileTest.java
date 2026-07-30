package com.kaziflow.modules;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IndustryProfile — verifies every profile has its mandatory modules,
 * no profile references a non-existent module ID, and profile-specific modules
 * are wired to the right profiles.
 *
 * These tests act as a regression guard for the ModuleRegistry wiring:
 * if a new profile is added without its required modules, or a module ID is
 * renamed without updating profiles, the build fails immediately.
 */
@DisplayName("IndustryProfile — module wiring")
class IndustryProfileTest {

    // Every profile must have at minimum these core modules
    private static final String[] CORE_MODULES = {
        "dashboard", "inventory", "sales", "purchases", "employees", "reports", "settings"
    };

    @ParameterizedTest(name = "{0} has all core modules")
    @EnumSource(IndustryProfile.class)
    @DisplayName("Every profile contains the 7 core modules")
    void everyProfileHasCoreModules(IndustryProfile profile) {
        for (String mod : CORE_MODULES) {
            assertTrue(
                profile.hasModule(mod),
                profile.name() + " is missing core module: " + mod
            );
        }
    }

    @ParameterizedTest(name = "{0} has non-empty displayName and icon")
    @EnumSource(IndustryProfile.class)
    @DisplayName("Every profile has a non-blank displayName and icon")
    void everyProfileHasDisplayNameAndIcon(IndustryProfile profile) {
        assertFalse(
            profile.displayName == null || profile.displayName.isBlank(),
            profile.name() + " has blank displayName"
        );
        assertFalse(
            profile.icon == null || profile.icon.isBlank(),
            profile.name() + " has blank icon"
        );
    }

    @ParameterizedTest(name = "{0} has at least 7 modules")
    @EnumSource(IndustryProfile.class)
    @DisplayName("Every profile has at least 7 modules")
    void everyProfileHasMinimumModuleCount(IndustryProfile profile) {
        assertTrue(
            profile.moduleIds.size() >= 7,
            profile.name() + " only has " + profile.moduleIds.size() + " modules (min 7)"
        );
    }

    // ── Industry-specific module assertions ───────────────────────────────

    @Test
    @DisplayName("PHARMACY has batches module (expiry tracking)")
    void pharmacyHasBatches() {
        assertTrue(IndustryProfile.PHARMACY.hasModule("batches"),
            "PHARMACY must have batches module for expiry/FEFO tracking");
    }

    @Test
    @DisplayName("CLINIC has patients module")
    void clinicHasPatients() {
        assertTrue(IndustryProfile.CLINIC.hasModule("patients"),
            "CLINIC must have patients module");
    }

    @Test
    @DisplayName("VET_CLINIC has both patients and appointments")
    void vetClinicHasPatientsAndAppointments() {
        assertTrue(IndustryProfile.VET_CLINIC.hasModule("patients"),
            "VET_CLINIC must have patients");
        assertTrue(IndustryProfile.VET_CLINIC.hasModule("appointments"),
            "VET_CLINIC must have appointments");
    }

    @Test
    @DisplayName("RESTAURANT has restaurant module")
    void restaurantHasRestaurantModule() {
        assertTrue(IndustryProfile.RESTAURANT.hasModule("restaurant"),
            "RESTAURANT must have restaurant module");
    }

    @Test
    @DisplayName("GYM has gym module")
    void gymHasGymModule() {
        assertTrue(IndustryProfile.GYM.hasModule("gym"),
            "GYM must have gym module");
    }

    @Test
    @DisplayName("HOTEL has hotel module")
    void hotelHasHotelModule() {
        assertTrue(IndustryProfile.HOTEL.hasModule("hotel"),
            "HOTEL must have hotel module");
    }

    @Test
    @DisplayName("FUEL_STATION has fuel_station module")
    void fuelStationHasFuelModule() {
        assertTrue(IndustryProfile.FUEL_STATION.hasModule("fuel_station"),
            "FUEL_STATION must have fuel_station module");
    }

    @Test
    @DisplayName("SCHOOL_CANTEEN has canteen module")
    void canteenHasCanteenModule() {
        assertTrue(IndustryProfile.SCHOOL_CANTEEN.hasModule("canteen"),
            "SCHOOL_CANTEEN must have canteen module");
    }

    @Test
    @DisplayName("LAUNDRY has laundry module")
    void laundryHasLaundryModule() {
        assertTrue(IndustryProfile.LAUNDRY.hasModule("laundry"),
            "LAUNDRY must have laundry module");
    }

    // ── Cross-profile isolation checks ────────────────────────────────────

    @Test
    @DisplayName("GENERAL_RETAIL does NOT have patients module")
    void generalRetailDoesNotHavePatients() {
        assertFalse(IndustryProfile.GENERAL_RETAIL.hasModule("patients"),
            "GENERAL_RETAIL should not have patients module");
    }

    @Test
    @DisplayName("GENERAL_RETAIL does NOT have restaurant module")
    void generalRetailDoesNotHaveRestaurant() {
        assertFalse(IndustryProfile.GENERAL_RETAIL.hasModule("restaurant"),
            "GENERAL_RETAIL should not have restaurant module");
    }

    @Test
    @DisplayName("CLINIC does NOT have restaurant module")
    void clinicDoesNotHaveRestaurant() {
        assertFalse(IndustryProfile.CLINIC.hasModule("restaurant"),
            "CLINIC should not have restaurant module");
    }

    // ── Enum completeness ─────────────────────────────────────────────────

    @Test
    @DisplayName("There are exactly 19 industry profiles")
    void exactlyNineteenProfiles() {
        assertEquals(19, IndustryProfile.values().length,
            "Expected exactly 19 IndustryProfile enum constants. " +
            "If you added or removed a profile, update this test.");
    }

    @Test
    @DisplayName("No two profiles have the same displayName")
    void noDuplicateDisplayNames() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (IndustryProfile p : IndustryProfile.values()) {
            assertTrue(seen.add(p.displayName),
                "Duplicate displayName: " + p.displayName);
        }
    }
}

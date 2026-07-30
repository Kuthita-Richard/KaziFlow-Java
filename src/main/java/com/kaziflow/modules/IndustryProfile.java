package com.kaziflow.modules;

import java.util.List;
import java.util.Map;

/**
 * IndustryProfile — defines the module set, POS layout, and terminology
 * for each supported industry vertical.
 *
 * On first run, the user picks their profile via ProfileSelector.
 * The selection is saved to the settings table as 'industry_profile'.
 *
 * Adding a new industry:
 *   1. Add an enum constant below
 *   2. Define its moduleIds, posLayout, and terminology
 *   3. Add the profile card to ProfileSelector UI
 */
public enum IndustryProfile {

    // ── Retail ────────────────────────────────────────────────────────────

    GENERAL_RETAIL(
        "General Retail / Hardware",
        "🏪",
        "Best for: hardware shops, wholesale, stationery, electronics, clothing",
        PosLayout.GRID,
        List.of("dashboard","inventory","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of()
    ),

    SUPERMARKET(
        "Supermarket / Grocery",
        "🛒",
        "Best for: supermarkets, grocery shops, fruits & veg, minimarts",
        PosLayout.GRID,
        List.of("dashboard","inventory","batches","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Item", "supplier","Distributor")
    ),

    LIQUOR_SHOP(
        "Liquor & Wines Shop",
        "🍾",
        "Best for: wines, spirits, beer wholesale, off-licences",
        PosLayout.GRID,
        List.of("dashboard","inventory","batches","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of()
    ),

    // ── Health & Medical ───────────────────────────────────────────────────

    PHARMACY(
        "Pharmacy / Chemist",
        "💊",
        "Best for: pharmacies, chemists, drug stores, medical supplies",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","batches","sales","purchases","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Medicine", "supplier","Distributor", "category","Drug Class")
    ),

    CLINIC(
        "Clinic / Hospital",
        "🏥",
        "Best for: clinics, hospitals, medical centres, dental clinics",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","appointments","patients","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Medical Supply", "customer","Patient", "sale","Consultation Bill")
    ),

    VET_CLINIC(
        "Veterinary Clinic",
        "🐾",
        "Best for: vet clinics, animal hospitals, pet pharmacies",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","batches","appointments","patients","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("customer","Pet Owner", "product","Vet Supply", "sale","Vet Bill")
    ),

    // ── Agriculture ────────────────────────────────────────────────────────

    AGROVET(
        "Agrovet / Agri Supplies",
        "🌱",
        "Best for: agrovets, seed shops, fertilizer shops, farm supplies",
        PosLayout.GRID,
        List.of("dashboard","inventory","batches","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Agri Product", "supplier","Agro Distributor")
    ),

    // ── Food & Beverage ────────────────────────────────────────────────────

    RESTAURANT(
        "Restaurant / Café / Hotel",
        "🍽️",
        "Best for: restaurants, cafes, fast food, hotels, canteens",
        PosLayout.TABLE_FIRST,
        List.of("dashboard","inventory","restaurant","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Menu Item", "customer","Guest", "sale","Bill",
               "category","Menu Category", "supplier","Food Supplier")
    ),

    BAKERY(
        "Bakery / Juicery / Food Production",
        "🥐",
        "Best for: bakeries, juice bars, food kiosks, catering",
        PosLayout.GRID,
        List.of("dashboard","inventory","batches","restaurant","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Product", "supplier","Ingredient Supplier")
    ),

    BUTCHERY(
        "Butchery / Fishmonger",
        "🥩",
        "Best for: butcheries, fishmongers, meat markets",
        PosLayout.GRID,
        List.of("dashboard","inventory","batches","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Cut / Item", "category","Meat Type")
    ),

    // ── Beauty & Wellness ──────────────────────────────────────────────────

    SALON(
        "Salon / Barbershop / Spa",
        "✂️",
        "Best for: salons, barbers, spas, nail salons, massage studios",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","appointments","sales","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Service", "customer","Client", "sale","Service Bill",
               "supplier","Product Supplier", "category","Service Category")
    ),

    GYM(
        "Gym / Fitness Centre",
        "💪",
        "Best for: gyms, fitness centres, yoga studios, martial arts schools",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","appointments","gym","sales","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("customer","Member", "product","Membership / Class", "sale","Membership Invoice")
    ),

    // ── Automotive ─────────────────────────────────────────────────────────

    AUTO_SPARES(
        "Auto Spares Shop",
        "🔩",
        "Best for: auto spares, motorcycle parts, tyre shops",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","workshop","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Part", "category","Part Category", "supplier","Parts Supplier")
    ),

    GARAGE(
        "Garage / Workshop / Jua Kali",
        "🔧",
        "Best for: garages, mechanics, electronics repair, jua kali",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","appointments","workshop","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("sale","Job Card", "customer","Vehicle Owner", "product","Part / Service")
    ),

    // ── Services ───────────────────────────────────────────────────────────

    LAUNDRY(
        "Laundry / Dry Cleaning",
        "👔",
        "Best for: laundries, dry cleaners, laundromats",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","laundry","sales","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("sale","Laundry Order", "customer","Client", "product","Service Type")
    ),

    HOTEL(
        "Hotel / Guest House / Lodging",
        "🏨",
        "Best for: hotels, guest houses, lodges, B&Bs",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","appointments","restaurant","hotel","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("customer","Guest", "sale","Guest Invoice", "product","Room / Service")
    ),

    FUEL_STATION(
        "Petrol / Fuel Station",
        "⛽",
        "Best for: petrol stations, fuel distributors, LPG depots",
        PosLayout.GRID,
        List.of("dashboard","inventory","fuel_station","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("product","Fuel / Product", "supplier","Oil Marketer", "category","Fuel Type")
    ),

    SCHOOL_CANTEEN(
        "School Canteen / Cafeteria",
        "🏫",
        "Best for: school canteens, college cafeterias, corporate cafeterias",
        PosLayout.GRID,
        List.of("dashboard","inventory","restaurant","canteen","sales","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("customer","Student / Staff", "sale","Canteen Order", "product","Meal / Snack")
    ),

    PRINTING_SHOP(
        "Printing Shop / Cyber Café",
        "🖨️",
        "Best for: print shops, sign shops, cyber cafes, photography studios",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","inventory","appointments","workshop","sales","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of("sale","Job Order", "customer","Client", "product","Service / Print Job")
    ),

    // ── SACCO / SHG (Phase 10A) ─────────────────────────────────────────────

    SACCO(
        "SACCO — Savings & Credit Co-operative",
        "🤝",
        "Best for: registered savings and credit co-operatives — member shares, reducing-balance loans, dividends",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","sacco","employees","finance","reports","settings","help"),
        Map.of("customer","Member")
    ),

    SHG_SACCO(
        "Self-Help Group / Chama",
        "👥",
        "Best for: informal savings groups and chamas — merry-go-round contributions, group savings, small loans",
        PosLayout.SEARCH_FIRST,
        List.of("dashboard","sacco","finance","reports","settings","help"),
        Map.of("customer","Member")
    ),

    // ── Custom ─────────────────────────────────────────────────────────────

    CUSTOM(
        "Custom — I'll choose my modules",
        "⚙️",
        "Advanced: manually select which modules to enable in Settings",
        PosLayout.GRID,
        List.of("dashboard","inventory","sales","purchases","employees","finance","reports","deni","quotations","delivery","payroll","settings","help"),
        Map.of()
    );

    // ── Fields ────────────────────────────────────────────────────────────

    public final String displayName;
    public final String icon;
    public final String description;
    public final PosLayout posLayout;
    public final List<String> moduleIds;          // which modules are active
    public final Map<String, String> terminology;  // label overrides

    IndustryProfile(String displayName, String icon, String description,
                    PosLayout posLayout, List<String> moduleIds,
                    Map<String, String> terminology) {
        this.displayName  = displayName;
        this.icon         = icon;
        this.description  = description;
        this.posLayout    = posLayout;
        this.moduleIds    = moduleIds;
        this.terminology  = terminology;
    }

    /** Get a terminology override, or return the default label if no override exists. */
    public String term(String key, String defaultLabel) {
        return terminology.getOrDefault(key, defaultLabel);
    }

    /** True if this profile has the given module enabled. */
    public boolean hasModule(String moduleId) {
        return moduleIds.contains(moduleId);
    }

    /** Parse from the string stored in the settings table. */
    public static IndustryProfile fromKey(String key) {
        if (key == null) return GENERAL_RETAIL;
        try { return IndustryProfile.valueOf(key.toUpperCase()); }
        catch (Exception e) { return GENERAL_RETAIL; }
    }

    /** POS layout style for this industry. */
    public enum PosLayout {
        GRID,         // product grid — retail default
        SEARCH_FIRST, // search box is primary, grid is secondary
        TABLE_FIRST   // pick table/room first, then menu
    }
}

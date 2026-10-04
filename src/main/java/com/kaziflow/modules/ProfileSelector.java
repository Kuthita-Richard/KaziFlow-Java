package com.kaziflow.modules;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ProfileSelector — first-run industry picker wizard.
 *
 * Shown once when no industry_profile exists in the settings table.
 * The user picks their industry and the selection is saved via ModuleRegistry.
 *
 * Can also be accessed later via Settings → Change Industry Profile.
 *
 * Rebuilt (v11.13) to fix a real selection bug: the previous version used
 * JavaFX ToggleButton + ToggleGroup, which allows clicking the
 * already-selected card to deselect it, leaving NOTHING selected — and the
 * code only updated the tracked selection when a card became selected, never
 * when everything became deselected, so the internal state could silently go
 * stale and disagree with what was visually highlighted. Rather than patch
 * around that well-known footgun, this version manages single-selection
 * manually: clicking any card always selects it (never toggles it off), full
 * stop. There is no code path that can leave zero cards selected.
 */
public class ProfileSelector {

    private BorderPane root;
    private IndustryProfile selected = IndustryProfile.GENERAL_RETAIL;
    private Runnable onComplete;

    private final Map<IndustryProfile, VBox> cardsByProfile = new LinkedHashMap<>();
    private Label selectedLabel;

    /** Short, human-readable names for modules — used to show "Includes: X, Y, Z" on each card. */
    private static final Map<String, String> MODULE_FRIENDLY_NAMES = Map.ofEntries(
        Map.entry("dashboard", "Dashboard"), Map.entry("inventory", "Inventory"),
        Map.entry("sales", "Sales & POS"), Map.entry("purchases", "Purchases"),
        Map.entry("employees", "HR"), Map.entry("payroll", "Payroll"),
        Map.entry("finance", "Finance"), Map.entry("reports", "Reports"),
        Map.entry("settings", "Settings"), Map.entry("help", "Help"),
        Map.entry("deni", "Credit Book"), Map.entry("sacco", "SACCO/Loans"),
        Map.entry("delivery", "Delivery"), Map.entry("quotations", "Quotations"),
        Map.entry("batches", "Batch/Expiry"), Map.entry("appointments", "Appointments"),
        Map.entry("patients", "Patients"), Map.entry("restaurant", "Restaurant Orders"),
        Map.entry("workshop", "Job Cards"), Map.entry("gym", "Memberships"),
        Map.entry("laundry", "Laundry Orders"), Map.entry("hotel", "Rooms & Bookings"),
        Map.entry("fuel_station", "Fuel Pumps"), Map.entry("canteen", "Canteen Accounts")
    );

    // Module ids present in almost every profile — not worth listing, they don't distinguish one
    // business type from another the way "Restaurant Orders" or "Fuel Pumps" does.
    private static final List<String> UNIVERSAL_MODULES = List.of("dashboard", "settings", "help");

    public ProfileSelector(Runnable onComplete) {
        this.onComplete = onComplete;
        buildUI();
    }

    public BorderPane getRoot() { return root; }

    private void buildUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #f8fafc;");

        VBox header = new VBox(6);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(28, 20, 16, 20));
        Label title = new Label("Welcome to "+com.kaziflow.brand.Brand.productFullName());
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subtitle = new Label("Pick the business type that's closest to yours — you can change this later in Settings.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        header.getChildren().addAll(title, subtitle);

        VBox content = new VBox(22);
        content.setPadding(new Insets(4, 28, 20, 28));

        for (CategoryGroup group : CATEGORIES) {
            content.getChildren().add(buildCategorySection(group));
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        HBox footer = new HBox(16);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(14, 28, 18, 28));
        footer.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");

        selectedLabel = new Label("Selected: " + selected.displayName);
        selectedLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button continueBtn = new Button("Continue →");
        continueBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; " +
            "-fx-background-radius: 8; -fx-pref-height: 38px; -fx-padding: 0 22; -fx-cursor: hand; -fx-font-size: 13px;");
        continueBtn.setOnAction(e -> {
            ModuleRegistry.getInstance().setProfile(selected);
            if (onComplete != null) onComplete.run();
        });

        footer.getChildren().addAll(selectedLabel, sp, continueBtn);

        // Select the default card's visuals to match the default `selected` field, so the
        // highlighted card and the footer label agree from the very first frame.
        highlightSelectedCard();

        root.setTop(header);
        root.setCenter(scroll);
        root.setBottom(footer);
    }

    // ── Category grouping (v11.13: "ordered properly" — grouped rather than raw enum order) ──

    private record CategoryGroup(String label, List<IndustryProfile> profiles) {}

    private static final List<CategoryGroup> CATEGORIES = List.of(
        new CategoryGroup("Retail & Trade", List.of(
            IndustryProfile.GENERAL_RETAIL, IndustryProfile.SUPERMARKET, IndustryProfile.LIQUOR_SHOP,
            IndustryProfile.PRINTING_SHOP, IndustryProfile.FUEL_STATION)),
        new CategoryGroup("Food & Hospitality", List.of(
            IndustryProfile.RESTAURANT, IndustryProfile.BAKERY, IndustryProfile.BUTCHERY,
            IndustryProfile.HOTEL, IndustryProfile.SCHOOL_CANTEEN)),
        new CategoryGroup("Health & Veterinary", List.of(
            IndustryProfile.PHARMACY, IndustryProfile.CLINIC, IndustryProfile.VET_CLINIC, IndustryProfile.AGROVET)),
        new CategoryGroup("Automotive & Repair", List.of(
            IndustryProfile.AUTO_SPARES, IndustryProfile.GARAGE)),
        new CategoryGroup("Personal Care & Fitness", List.of(
            IndustryProfile.SALON, IndustryProfile.GYM, IndustryProfile.LAUNDRY)),
        new CategoryGroup("Financial Groups", List.of(
            IndustryProfile.SACCO, IndustryProfile.SHG_SACCO)),
        new CategoryGroup("Other", List.of(
            IndustryProfile.CUSTOM))
    );

    private VBox buildCategorySection(CategoryGroup group) {
        VBox section = new VBox(10);
        Label header = new Label(group.label());
        header.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-padding: 0 0 2 2;");

        FlowPane flow = new FlowPane();
        flow.setHgap(12);
        flow.setVgap(12);
        for (IndustryProfile profile : group.profiles()) {
            flow.getChildren().add(profileCard(profile));
        }

        section.getChildren().addAll(header, flow);
        return section;
    }

    // ── Card rendering (v11.13: smaller, manually single-selected, shows included modules) ──

    private VBox profileCard(IndustryProfile profile) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(10));
        card.setPrefWidth(168);
        card.setMinHeight(132); // +0.5x the original 88px, per feedback
        card.setMaxWidth(168);
        card.setStyle(cardStyle(false));
        card.setCursor(javafx.scene.Cursor.HAND);

        HBox titleRow = new HBox(6);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label(profile.icon);
        icon.setStyle("-fx-font-size: 16px;");
        Label name = new Label(profile.displayName);
        name.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-wrap-text: true;");
        name.setMaxWidth(130);
        titleRow.getChildren().addAll(icon, name);

        Label modules = new Label("Includes: " + includedModulesSummary(profile));
        modules.setStyle("-fx-font-size: 9px; -fx-text-fill: #64748b; -fx-wrap-text: true;");
        modules.setMaxWidth(148);

        card.getChildren().addAll(titleRow, modules);

        // Several profiles share an identical module SET (e.g. Supermarket/Liquor Shop/Butchery
        // are all just "inventory + batch tracking" today — there's no separate backend module
        // per retail vertical yet). Where that's true, the one thing that IS genuinely different
        // is the terminology this profile uses — show it instead of implying a difference in
        // modules that doesn't actually exist. Profiles with no override (Map.of()) show nothing
        // extra here rather than a fabricated distinction.
        if (!profile.terminology.isEmpty()) {
            String terms = String.join(", ", profile.terminology.values());
            Label terminology = new Label("Calls things: " + terms);
            terminology.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-wrap-text: true;");
            terminology.setMaxWidth(148);
            card.getChildren().add(terminology);
        }

        // Single click handler, no ToggleButton/ToggleGroup involved — selecting a card always
        // selects it. There's no interaction pattern here that can leave nothing selected.
        card.setOnMouseClicked(e -> {
            selected = profile;
            selectedLabel.setText("Selected: " + profile.displayName);
            highlightSelectedCard();
        });

        cardsByProfile.put(profile, card);
        return card;
    }

    private void highlightSelectedCard() {
        for (Map.Entry<IndustryProfile, VBox> entry : cardsByProfile.entrySet()) {
            entry.getValue().setStyle(cardStyle(entry.getKey() == selected));
        }
    }

    private String cardStyle(boolean isSelected) {
        String base = "-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-width: 2;";
        return isSelected
            ? base + " -fx-background-color: #eff6ff; -fx-border-color: #2563eb;"
            : base + " -fx-background-color: white; -fx-border-color: #e2e8f0;";
    }

    /** Builds the short "Includes: ..." text for a card, skipping universal modules, capped so cards stay small. */
    private String includedModulesSummary(IndustryProfile profile) {
        List<String> distinctive = profile.moduleIds.stream()
            .filter(id -> !UNIVERSAL_MODULES.contains(id))
            .map(id -> MODULE_FRIENDLY_NAMES.getOrDefault(id, id))
            .toList();
        if (distinctive.isEmpty()) return "Core modules only";
        int shown = Math.min(4, distinctive.size());
        String text = String.join(", ", distinctive.subList(0, shown));
        if (distinctive.size() > shown) text += " +" + (distinctive.size() - shown) + " more";
        return text;
    }
}

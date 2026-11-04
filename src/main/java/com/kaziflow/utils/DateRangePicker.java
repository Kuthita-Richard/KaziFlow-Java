package com.kaziflow.utils;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The one period control: a preset box (today … last year, all time, custom dates), two date pickers that are editable
 * only for "Custom dates…", and a caption that always says exactly which dates are meant (or why the choice is invalid).
 * Used by the export dialog now and by the analytics pages (R2) later, so every screen means the same thing by
 * "this month".
 *
 * {@link #getRange()} is the resolved {@link DateRange}, or {@code null} while the choice is invalid (a custom range with
 * no dates or with the start after the end). A custom range may leave one end empty ("from X" / "up to Y").
 * "Today" comes from a supplier so tests can fix it.
 */
public class DateRangePicker extends VBox {
    private final ComboBox<DateRange.Preset> preset = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final Label caption = new Label();
    private final Supplier<LocalDate> today;
    private final ObjectProperty<DateRange> range = new SimpleObjectProperty<>();
    private boolean updating;

    // Day-first everywhere: "4 Nov 2026" is shown; typed "04/11/2026", "4/11/2026", "4 Nov 2026" and "2026-11-04" are read day-first,
    // so 11/10 can never silently mean 10 November.
    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter[] PARSE = {
        SHOW, DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"), DateTimeFormatter.ISO_LOCAL_DATE };

    /** A typed date, day-first; null when blank or not a real date (e.g. 31/02/2026). */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        for (DateTimeFormatter f : PARSE) {
            try { return LocalDate.parse(text.trim(), f.withResolverStyle(java.time.format.ResolverStyle.STRICT)); }
            catch (DateTimeParseException ignored) { /* try the next format */ }
        }
        return null;
    }

    private static final StringConverter<LocalDate> CONVERTER = new StringConverter<>() {
        @Override public String toString(LocalDate d) { return d == null ? "" : SHOW.format(d); }
        @Override public LocalDate fromString(String s) {
            LocalDate d = parseDate(s);
            if (d == null && s != null && !s.isBlank()) throw new DateTimeParseException("Not a date", s, 0);   // the field reverts
            return d;
        }
    };

    public DateRangePicker() { this(DateRange.Preset.THIS_MONTH, LocalDate::now); }

    public DateRangePicker(DateRange.Preset initial, Supplier<LocalDate> today) {
        super(8);
        this.today = today;
        preset.getItems().addAll(DateRange.Preset.values());
        preset.setMaxWidth(Double.MAX_VALUE);
        from.setConverter(CONVERTER); to.setConverter(CONVERTER);
        from.setPromptText("Start date");
        to.setPromptText("End date");
        HBox dates = new HBox(8, labelled("From", from), labelled("To", to));
        dates.setAlignment(Pos.CENTER_LEFT);
        caption.setWrapText(true);
        caption.setPadding(new Insets(2, 0, 0, 0));
        caption.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        getChildren().addAll(preset, dates, caption);
        preset.valueProperty().addListener((o, a, b) -> refresh());
        from.valueProperty().addListener((o, a, b) -> { if (!updating) refresh(); });
        to.valueProperty().addListener((o, a, b) -> { if (!updating) refresh(); });
        preset.setValue(initial == null ? DateRange.Preset.THIS_MONTH : initial);
        refresh();
    }

    private static VBox labelled(String text, DatePicker p) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        p.setPrefWidth(150);
        return new VBox(2, l, p);
    }

    private void refresh() {
        DateRange.Preset p = preset.getValue();
        boolean custom = p == DateRange.Preset.CUSTOM;
        from.setDisable(!custom);
        to.setDisable(!custom);
        if (!custom) {
            DateRange r = DateRange.of(p, today.get());
            updating = true;
            try { from.setValue(r.from()); to.setValue(r.to()); } finally { updating = false; }
            show(r);
            return;
        }
        LocalDate f = from.getValue(), t = to.getValue();
        if (f == null && t == null) { invalid("Pick a start date, an end date, or both."); return; }
        if (f != null && t != null && f.isAfter(t)) { invalid("The start date is after the end date."); return; }
        show(new DateRange(f, t));
    }

    private void show(DateRange r) {
        range.set(r);
        long d = r.days();
        caption.setText(r.label() + (d > 0 ? "  (" + d + (d == 1 ? " day)" : " days)") : ""));
        caption.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
    }

    private void invalid(String why) {
        range.set(null);
        caption.setText(why);
        caption.setStyle("-fx-font-size: 12px; -fx-text-fill: #dc2626;");
    }

    /** The resolved period, or null while the choice is invalid. */
    public DateRange getRange() { return range.get(); }
    public boolean isValid() { return range.get() != null; }
    public ReadOnlyObjectProperty<DateRange> rangeProperty() { return range; }

    public DateRange.Preset getPreset() { return preset.getValue(); }
    public void setPreset(DateRange.Preset p) { preset.setValue(p); }

    /** Switches to "Custom dates…" with the given ends (either may be null). */
    public void setCustom(LocalDate f, LocalDate t) {
        updating = true;
        try { preset.setValue(DateRange.Preset.CUSTOM); from.setValue(f); to.setValue(t); } finally { updating = false; }
        refresh();
    }

    public String captionText() { return caption.getText(); }
    public boolean datesEditable() { return !from.isDisabled() && !to.isDisabled(); }
    public LocalDate shownFrom() { return from.getValue(); }
    public LocalDate shownTo() { return to.getValue(); }
}

package com.kaziflow.trust;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the release-R1 "trust" fixes fixed (docs/design/product-depth-plan.md §1). Each rule is something that once
 * made the product say something untrue; scanning the SOURCE (string literals only, not comments) means a regression
 * fails the build with the file and line, long before a customer sees it.
 */
class TrustGuardTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    /** phrase -> why it must not appear in any string literal. */
    private static final List<String[]> BANNED = List.of(
        new String[]{"ETIMS Serial", "a receipt line presenting a locally generated number as a KRA eTIMS serial"},
        new String[]{"KRA-%s", "a format string that generates a KRA-looking serial locally (must be LOCAL-…; the migration's LIKE 'KRA-%' is fine)"},
        new String[]{"\"KRA-", "a hardcoded KRA-style serial; real eTIMS numbers must come from KRA, not be invented"},
        new String[]{"Trusted by", "an unsubstantiated customer-count claim on the login screen"},
        new String[]{"Sign in with Google", "a sign-in button with no OAuth client behind it"},
        new String[]{"OR CONTINUE WITH", "the divider for the removed Google button"},
        new String[]{"PieChart.Data(\"Cement\"", "invented sample slices drawn as if they were the user's data"},
        new String[]{"PieChart.Data(\"Steel\"", "invented sample slices drawn as if they were the user's data"},
        new String[]{"totalExpenses * 0.", "an invented percentage split of expenses presented as real accounting lines"},
        new String[]{"getWeekRevenue()", "this-week revenue printed under a 'Total Revenue' label in an export of other rows"},
        // v11.35 sweep: stat cards show the real figure or "—", never a sample (guide §4.12)
        new String[]{"1,240 SKUs", "a product count nothing computed"},
        new String[]{"+12% vs last month", "a trend nothing computed"},
        new String[]{"+2.4% vs last month", "a trend nothing computed"},
        new String[]{"56% of sales", "a payment-mix share nothing computed (use TopShare)"},
        new String[]{"42% of total", "a top-category share nothing computed (use ExpenseStats / TopShare)"},
        new String[]{"4200000", "a sample revenue shown when the real value is 0"},
        new String[]{"12500000", "a sample inventory value shown when the real value is 0"},
        new String[]{"1240000", "a sample expense total shown when the real value is 0"},
        new String[]{"103333", "a sample monthly average shown when there is no history"},
        new String[]{"1800000", "a sample net profit shown when the real value is not positive"},
        // v11.38 sweep part 2 (people and purchases)
        new String[]{"81% rate", "an attendance rate nothing computed"},
        new String[]{"93% active", "a supplier share nothing computed"},
        new String[]{"Industry standard", "a payment-terms claim in place of the suppliers' real average"},
        new String[]{"October 2024", "a month typed in code (use DateRange.monthLabel)"},
        new String[]{"4 departments", "a department count nothing computed"},
        new String[]{"Annual 21d", "leave policies typed in code instead of read from data"},
        new String[]{"↑ Current period", "an up arrow shown whatever the sign of the figure"},
        // v11.42: no sample data in a customer's database, and document numbers come from NumberSequence, never COUNT(*)+1
        new String[]{"Family vacation", "an invented leave request shown in the leave list"},
        new String[]{"totalCustomers - 2", "'repeat buyers' computed as total customers minus 2"},
        new String[]{"seedIfEmpty", "demo customers inserted into a real database (use DemoDataService, opt-in)"},
        new String[]{"INV-%04d", "a COUNT-based invoice number (use NumberSequence)"},
        new String[]{"PO-%04d", "a COUNT-based purchase number (use NumberSequence)"},
        new String[]{"QT-%04d", "a COUNT-based quotation number (use NumberSequence)"},
        new String[]{"RET-%04d", "a COUNT-based return number (use NumberSequence)"},
        new String[]{"TRF-%04d", "a COUNT-based transfer number (use NumberSequence)"},
        new String[]{"EMP-%03d", "a COUNT-based employee number (use NumberSequence)"});

    @Test
    @DisplayName("no source string says something the product cannot back up")
    void noUntrueStrings() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (f.endsWith("ReportExportService.java") || f.toString().contains("dao")) { /* still scanned */ }
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String t = lines.get(i).trim();
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
                    Matcher m = LITERAL.matcher(t);
                    StringBuilder literals = new StringBuilder();
                    while (m.find()) literals.append(m.group()).append(' ');
                    String scan = literals + " " + t;       // phrases like PieChart.Data("Cement" span code + literal
                    for (String[] b : BANNED)
                        if (scan.contains(b[0]) && !isDefinition(f, t, b[0]))
                            offenders.add(MAIN.relativize(f) + ":" + (i + 1) + "  [" + b[1] + "]  " + t);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Untrue / invented text found:\n  " + String.join("\n  ", offenders));
    }

    /** The DAO that DEFINES getWeekRevenue() is fine; only calling it inside an export is the bug. */
    private static boolean isDefinition(Path f, String line, String phrase) {
        return phrase.equals("getWeekRevenue()") && !f.endsWith("ReportExportService.java");
    }

    @Test
    @DisplayName("generated local numbers can never be labelled as KRA's")
    void localNumbersAreLabelledLocal() throws IOException {
        String src = Files.readString(MAIN.resolve("com/kaziflow/services/ETIMSService.java"));
        assertTrue(src.contains("LOCAL_PREFIX"), "ETIMSService must label its counter with LOCAL_PREFIX");
        assertTrue(src.contains("isConnected() { return false; }"), "until real transmission exists, isConnected() must say false");
        String receipt = Files.readString(MAIN.resolve("com/kaziflow/services/ReceiptPrinter.java"));
        assertTrue(receipt.contains("getTransmittedSerial"), "receipts may only print a number KRA actually received");
    }
}

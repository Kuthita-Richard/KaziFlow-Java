package com.kaziflow.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Keeps the branding "data, not code" for good.
 *
 * <ul>
 *   <li>No Java source may hardcode the product name inside a string literal — it must come from {@link Brand}.
 *       The few literals that MUST stay fixed (they identify existing installations) are listed below with the reason.</li>
 *   <li>The names that were invented in the past ("KaziFlow Systems Ltd", "KaziFlow Technologies Ltd") and the stale
 *       "v2.4.0" must not come back.</li>
 *   <li>The installers must show the same publisher / product name as brand.properties.</li>
 *   <li>Every brand image the app and installers reference must exist.</li>
 * </ul>
 * Pure file scanning — no JavaFX, no database.
 */
class BrandGuardTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    /** file (relative to src/main/java/com/kaziflow) -> why a "KaziFlow" literal must stay there. */
    private static final List<String[]> ALLOWED = List.of(
        new String[]{"utils/AppPaths.java", "data folder name of every existing installation — must not follow the brand"},
        new String[]{"license/LicenseService.java", "HMAC signing secret: changing it would invalidate every issued licence"},
        new String[]{"services/UpdateService.java", "HTTP User-Agent token for the update check — technical, not user-visible"});

    @Test
    @DisplayName("no source file hardcodes the product name in a string literal (except the documented technical ones)")
    void noHardcodedProductName() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String rel = Path.of("src/main/java/com/kaziflow").relativize(file).toString().replace('\\', '/');
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) continue;
                Matcher m = LITERAL.matcher(line);
                while (m.find()) {
                    if (!m.group().contains("KaziFlow")) continue;
                    boolean allowed = ALLOWED.stream().anyMatch(a -> a[0].equals(rel));
                    if (!allowed) offenders.add(rel + ":" + (i + 1) + "  " + trimmed);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Hardcoded product name — use Brand.productName()/productFullName():\n  " + String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("invented company names and the stale version number are gone")
    void noStaleStrings() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (String bad : List.of("KaziFlow Systems Ltd", "KaziFlow Technologies Ltd", "v2.4.0", "Richard Kuthita"))
                if (text.contains(bad)) offenders.add(file + " contains \"" + bad + "\"");
        }
        assertTrue(offenders.isEmpty(), String.join("\n", offenders));
    }

    @Test
    @DisplayName("brand.properties defines every name and the hierarchy reads Tihems → Tihems Software → product")
    void brandDefinesEveryName() {
        Brand.reload();
        for (String v : new String[]{Brand.groupName(), Brand.departmentName(), Brand.productName(), Brand.productFullName(),
                Brand.tagline(), Brand.supportEmail(), Brand.website()})
            assertFalse(v.isBlank(), "a brand name is blank");
        assertEquals("Tihems", Brand.groupName());
        assertEquals("Tihems Software", Brand.departmentName());
        assertTrue(Brand.productFullName().startsWith(Brand.productName()));
        assertEquals("Tihems Software · a Tihems company", Brand.lineage());
        assertTrue(Brand.copyright().contains("Tihems Software"));
        assertEquals("Powered by " + Brand.productFullName(), Brand.poweredBy());
    }

    @Test
    @DisplayName("installer scripts show the same publisher and product name as brand.properties")
    void installersMatchBrand() throws IOException {
        Brand.reload();
        String win = Files.readString(Path.of("installer/build-windows.bat"));
        String mac = Files.readString(Path.of("installer/build-macos.sh"));
        String lin = Files.readString(Path.of("installer/build-linux.sh"));
        assertTrue(win.contains("VENDOR=" + Brand.departmentName()), "Windows installer VENDOR differs from brand");
        assertTrue(mac.contains("VENDOR=\"" + Brand.departmentName() + "\""), "macOS installer VENDOR differs from brand");
        assertTrue(lin.contains("VENDOR=\"" + Brand.departmentName() + "\""), "Linux installer VENDOR differs from brand");
        assertTrue(win.contains("APP_NAME=" + Brand.productFullName()), "Windows installer APP_NAME differs from brand");
        assertTrue(mac.contains("APP_NAME=\"" + Brand.productFullName() + "\""), "macOS installer APP_NAME differs from brand");
    }

    @Test
    @DisplayName("every brand image exists, is a real PNG/ICO, and the logos are transparent")
    void brandAssetsExist() throws IOException {
        Path res = Path.of("src/main/resources/brand");
        for (String f : Set.of("logo-dark.png", "logo-light.png", "mark-dark.png", "mark-light.png", "icon-16.png", "icon-32.png",
                "icon-48.png", "icon-64.png", "icon-128.png", "icon-256.png", "icon-512.png", "favicon-32.png", "icon.ico", "favicon.ico")) {
            Path p = res.resolve(f);
            assertTrue(Files.isRegularFile(p), "missing brand asset " + f);
            byte[] head = Files.readAllBytes(p);
            boolean png = head.length > 8 && head[0] == (byte) 0x89 && head[1] == 'P';
            boolean ico = head.length > 4 && head[0] == 0 && head[1] == 0 && head[2] == 1 && head[3] == 0;
            assertTrue(png || ico, f + " is not a valid PNG/ICO");
        }
        for (String f : List.of("icon_16.png", "icon_256.png", "icon_1024.png", "kaziflow.ico", "kaziflow_256.png",
                "kaziflow.iconset/icon_512x512@2x.png"))
            assertTrue(Files.isRegularFile(Path.of("installer/icons", f)), "missing installer icon " + f);
        // transparent corner = the background really was removed (not a baked-in box)
        var img = javax.imageio.ImageIO.read(res.resolve("logo-light.png").toFile());
        assertEquals(0, (img.getRGB(1, 1) >>> 24), "logo-light.png must have a transparent background");
        var dark = javax.imageio.ImageIO.read(res.resolve("logo-dark.png").toFile());
        assertEquals(0, (dark.getRGB(1, 1) >>> 24), "logo-dark.png must have a transparent background");
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}

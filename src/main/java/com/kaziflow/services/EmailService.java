package com.kaziflow.services;

import com.kaziflow.database.DatabaseManager;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

/**
 * EmailService — sends receipts via SMTP (Phase 10E).
 *
 * Deliberately implemented as raw SMTP over an implicit-TLS socket (port 465)
 * rather than pulling in Jakarta Mail: this sandbox's network allowlist only
 * covers a handful of package registries, not Maven Central, so a new
 * dependency couldn't reliably be added or verified here. Implicit TLS avoids
 * the extra complexity of a STARTTLS upgrade handshake mid-connection.
 *
 * Configure in Settings → Email Receipts. Credentials stored in the settings
 * table (keys prefixed "smtp_"). Works with any standard SMTP-over-SSL
 * provider (Gmail app passwords, SendGrid, Mailgun, etc.) on port 465.
 */
public class EmailService {

    private static EmailService instance;
    public static synchronized EmailService getInstance() {
        if (instance == null) instance = new EmailService();
        return instance;
    }

    private String host, username, password, fromName;
    private int port = 465;

    private EmailService() { reload(); }

    public void reload() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             java.sql.Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT key, value FROM settings WHERE key LIKE 'smtp_%'")) {
            while (rs.next()) {
                String key = rs.getString("key");
                String value = rs.getString("value");
                switch (key) {
                    case "smtp_host" -> host = value;
                    case "smtp_port" -> { try { port = Integer.parseInt(value); } catch (Exception ignored) {} }
                    case "smtp_username" -> username = value;
                    case "smtp_password" -> password = value;
                    case "smtp_from_name" -> fromName = value;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank() && username != null && !username.isBlank() && password != null;
    }

    public void saveSettings(String host, int port, String username, String password, String fromName) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)")) {
            String[][] kv = {
                {"smtp_host", host}, {"smtp_port", String.valueOf(port)},
                {"smtp_username", username}, {"smtp_password", password}, {"smtp_from_name", fromName}
            };
            for (String[] pair : kv) {
                ps.setString(1, pair[0]); ps.setString(2, pair[1]);
                ps.executeUpdate();
            }
        } catch (Exception e) { e.printStackTrace(); }
        reload();
    }

    /**
     * Sends a plain-text email synchronously — callers should run this off the
     * JavaFX Application Thread (e.g. via AsyncTask), the same convention already
     * used for MpesaService/AfricasTalkingService network calls.
     * Returns null on success, or an error message on failure.
     */
    public String send(String toEmail, String subject, String bodyText) {
        if (!isConfigured()) return "Email is not configured. Go to Settings → Email Receipts.";
        if (toEmail == null || !toEmail.contains("@")) return "Please enter a valid email address.";
        // toEmail and subject each land on their own line of the SMTP protocol (RCPT TO / a
        // header) — a pasted CR or LF in either would let untrusted input inject extra SMTP
        // commands or extra headers. bodyText is fine as-is: it's meant to contain newlines,
        // and dot-stuffing below already makes its DATA transmission safe.
        if (containsLineBreak(toEmail) || containsLineBreak(subject)) {
            return "Email address or subject contains an invalid line break.";
        }

        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
                socket.setSoTimeout(15000);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                OutputStream out = socket.getOutputStream();

                readResponse(in, "220"); // server greeting

                sendCommand(out, "EHLO kaziflow-erp");
                readResponse(in, "250");

                sendCommand(out, "AUTH LOGIN");
                readResponse(in, "334");
                sendCommand(out, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
                readResponse(in, "334");
                sendCommand(out, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
                readResponse(in, "235"); // authentication successful

                sendCommand(out, "MAIL FROM:<" + username + ">");
                readResponse(in, "250");
                sendCommand(out, "RCPT TO:<" + toEmail + ">");
                readResponse(in, "250");

                sendCommand(out, "DATA");
                readResponse(in, "354");

                String from = (fromName != null && !fromName.isBlank() ? fromName : "KaziFlow ERP") + " <" + username + ">";
                String normalizedBody = bodyText.replace("\r\n", "\n").replace("\n", "\r\n");
                // RFC 5321 §4.5.2 dot-stuffing: any line that starts with a literal "."
                // must have it doubled, or an SMTP server reads that line as the end-of-DATA
                // marker and silently truncates the message right there. Receipts are
                // line-based text (separators, amounts) — genuinely reachable, not a
                // theoretical edge case.
                String stuffedBody = normalizedBody.lines()
                    .map(line -> line.startsWith(".") ? "." + line : line)
                    .reduce((a, b) -> a + "\r\n" + b)
                    .orElse("");
                String message = "From: " + from + "\r\n"
                    + "To: " + toEmail + "\r\n"
                    + "Subject: " + subject + "\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "\r\n"
                    + stuffedBody
                    + "\r\n.";
                sendCommand(out, message);
                readResponse(in, "250"); // message accepted

                sendCommand(out, "QUIT");
            }
            return null; // success
        } catch (IOException e) {
            return "Could not send email: " + e.getMessage();
        }
    }

    private boolean containsLineBreak(String s) {
        return s != null && (s.indexOf('\r') >= 0 || s.indexOf('\n') >= 0);
    }

    private void sendCommand(OutputStream out, String command) throws IOException {
        out.write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Reads one SMTP response line and throws if it doesn't start with the expected status code. */
    private String readResponse(BufferedReader in, String expectedCode) throws IOException {
        String line = in.readLine();
        if (line == null || !line.startsWith(expectedCode)) {
            throw new IOException("Unexpected SMTP response (expected " + expectedCode + "): " + line);
        }
        // Multi-line responses continue with "250-"; keep reading until the final "250 " line.
        while (line != null && line.length() > 3 && line.charAt(3) == '-') {
            line = in.readLine();
        }
        return line;
    }
}

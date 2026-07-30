package com.kaziflow.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kaziflow.database.DatabaseManager;
import okhttp3.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * AfricasTalkingService — SMS integration via the Africa's Talking API.
 *
 * Used as an SMS fallback channel for customers without WhatsApp:
 *   - Sale receipts
 *   - Batch/product expiry alerts
 *   - Appointment / gym / laundry / workshop reminders
 *   - Deni Book (credit) payment reminders
 *
 * Configure credentials in Settings → SMS (Africa's Talking).
 * Credentials are stored in the settings table (keys prefixed "at_").
 *
 * Sandbox docs: https://developers.africastalking.com/docs/sms/sending/bulk
 *   Sandbox base URL:    https://api.sandbox.africastalking.com/version1/messaging
 *   Production base URL: https://api.africastalking.com/version1/messaging
 *   Sandbox username is always "sandbox".
 */
public class AfricasTalkingService {

    private static final String SANDBOX_URL    = "https://api.sandbox.africastalking.com/version1/messaging";
    private static final String PRODUCTION_URL = "https://api.africastalking.com/version1/messaging";

    private static AfricasTalkingService instance;
    private final OkHttpClient http = new OkHttpClient();
    private final Gson gson = new Gson();

    private String apiKey;
    private String username;
    private String senderId;     // optional registered Sender ID / Shortcode
    private boolean useSandbox = true;

    private AfricasTalkingService() {
        loadCredentials();
    }

    public static AfricasTalkingService getInstance() {
        if (instance == null) instance = new AfricasTalkingService();
        return instance;
    }

    // ─── Configuration ──────────────────────────────────────────────────────

    public void loadCredentials() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             ResultSet rs = conn.createStatement().executeQuery(
                 "SELECT key, value FROM settings WHERE key LIKE 'at_%'")) {
            while (rs.next()) {
                String key = rs.getString("key");
                String val = rs.getString("value");
                switch (key) {
                    case "at_api_key"  -> apiKey   = val;
                    case "at_username" -> username = val;
                    case "at_sender_id" -> senderId = val;
                    case "at_sandbox"  -> useSandbox = !"false".equals(val);
                }
            }
        } catch (Exception e) {
            System.err.println("[AfricasTalking] Could not load credentials: " + e.getMessage());
        }
        // Sandbox always uses username "sandbox" regardless of saved value
        if (useSandbox) username = "sandbox";
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
            && username != null && !username.isBlank();
    }

    public boolean isSandbox() { return useSandbox; }

    private String baseUrl() {
        return useSandbox ? SANDBOX_URL : PRODUCTION_URL;
    }

    // ─── Send SMS ────────────────────────────────────────────────────────────

    /**
     * Sends an SMS to a single recipient.
     * BLOCKING — call from a background thread (AsyncTask.run).
     *
     * @param phone   recipient phone number. Accepts 07xx / 01xx (converted to +254)
     *                or already-international +254xxxxxxxxx format.
     * @param message message body (will be truncated by carriers beyond ~160
     *                 chars per SMS segment; long messages are billed as
     *                 multiple segments by Africa's Talking automatically)
     * @return a short human-readable status string, e.g. "Sent" or an error message
     */
    public String send(String phone, String message) {
        if (!isConfigured()) {
            return "⚠️ SMS not configured. Go to Settings → SMS to add your Africa's Talking API key.";
        }

        String to = normalizePhone(phone);
        if (to == null) {
            return "⚠️ Invalid phone number: " + phone;
        }

        FormBody.Builder form = new FormBody.Builder()
            .add("username", username)
            .add("to", to)
            .add("message", message);
        if (senderId != null && !senderId.isBlank() && !useSandbox) {
            form.add("from", senderId);
        }

        Request request = new Request.Builder()
            .url(baseUrl())
            .header("apiKey", apiKey)
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(form.build())
            .build();

        try (Response response = http.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                return "⚠️ SMS failed (HTTP " + response.code() + "): " + body;
            }
            JsonObject json = gson.fromJson(body, JsonObject.class);
            JsonObject smsData = json.getAsJsonObject("SMSMessageData");
            if (smsData == null) return "⚠️ Unexpected response: " + body;

            String status = smsData.get("Message").getAsString();
            // Sandbox/Live both return per-recipient status in "Recipients" array
            var recipients = smsData.getAsJsonArray("Recipients");
            if (recipients != null && recipients.size() > 0) {
                JsonObject first = recipients.get(0).getAsJsonObject();
                String recStatus = first.has("status") ? first.get("status").getAsString() : status;
                String cost      = first.has("cost") ? first.get("cost").getAsString() : "";
                return "Success".equalsIgnoreCase(recStatus)
                    ? "✅ Sent" + (cost.isEmpty() ? "" : " (" + cost + ")")
                    : "⚠️ " + recStatus;
            }
            return status;
        } catch (IOException e) {
            return "⚠️ Network error: " + e.getMessage();
        } catch (Exception e) {
            return "⚠️ SMS error: " + e.getMessage();
        }
    }

    /**
     * Converts Kenyan-style local numbers (07xx.../01xx...) to international
     * +254 format. Returns null if the input doesn't look like a valid phone.
     */
    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+254") && digits.length() == 13) return digits;
        if (digits.startsWith("254") && digits.length() == 12) return "+" + digits;
        if (digits.startsWith("0") && digits.length() == 10) return "+254" + digits.substring(1);
        if ((digits.startsWith("7") || digits.startsWith("1")) && digits.length() == 9) return "+254" + digits;
        return null;
    }

    // ─── Settings persistence helper ───────────────────────────────────────

    public static void saveSetting(String key, String value) {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

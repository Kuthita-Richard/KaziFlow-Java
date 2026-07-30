package com.kaziflow.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.kaziflow.database.DatabaseManager;
import okhttp3.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * M-Pesa Daraja API integration.
 *
 * Handles:
 *  - OAuth token generation
 *  - STK Push (Lipa na M-Pesa Online)
 *  - C2B payment simulation (for testing)
 *
 * Configure credentials in Settings → M-Pesa Integration.
 */
public class MpesaService {

    private static final String SANDBOX_BASE = "https://sandbox.safaricom.co.ke";
    private static final String PRODUCTION_BASE = "https://api.safaricom.co.ke";

    private static MpesaService instance;
    private final OkHttpClient http = new OkHttpClient();
    private final Gson gson = new Gson();

    private String consumerKey;
    private String consumerSecret;
    private String tillNumber;
    private String paybillNumber;
    private String passkey = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919"; // sandbox default
    private boolean useSandbox = true; // switch to false for production

    private String cachedToken;
    private LocalDateTime tokenExpiry;

    private MpesaService() {
        loadCredentials();
    }

    public static MpesaService getInstance() {
        if (instance == null) instance = new MpesaService();
        return instance;
    }

    // ─── Configuration ──────────────────────────────────────────────────────────

    public void loadCredentials() {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT key, value FROM settings WHERE key LIKE 'mpesa_%'")) {
            while (rs.next()) {
                String key = rs.getString("key");
                String val = rs.getString("value");
                switch (key) {
                    case "mpesa_consumer_key"    -> consumerKey    = val;
                    case "mpesa_consumer_secret" -> consumerSecret = val;
                    case "mpesa_till_number"     -> tillNumber     = val;
                    case "mpesa_paybill"         -> paybillNumber  = val;
                    case "mpesa_passkey"         -> passkey        = (val != null && !val.isEmpty()) ? val : passkey;
                    case "mpesa_sandbox"         -> useSandbox     = !"false".equals(val);
                }
            }
        } catch (Exception e) {
            System.err.println("[M-Pesa] Could not load credentials: " + e.getMessage());
        }
    }

    public boolean isConfigured() {
        return consumerKey != null && !consumerKey.isEmpty() &&
               consumerSecret != null && !consumerSecret.isEmpty();
    }

    private String baseUrl() {
        return useSandbox ? SANDBOX_BASE : PRODUCTION_BASE;
    }

    // ─── OAuth Token ──────────────────────────────────────────────────────────

    public String getAccessToken() throws IOException {
        if (cachedToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        String credentials = Base64.getEncoder().encodeToString(
            (consumerKey + ":" + consumerSecret).getBytes()
        );

        Request request = new Request.Builder()
            .url(baseUrl() + "/oauth/v1/generate?grant_type=client_credentials")
            .header("Authorization", "Basic " + credentials)
            .get()
            .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Token request failed: " + response.code());
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            cachedToken = json.get("access_token").getAsString();
            tokenExpiry = LocalDateTime.now().plusSeconds(3500); // token valid ~1hr
            return cachedToken;
        }
    }

    // ─── STK Push ─────────────────────────────────────────────────────────────

    /**
     * Sends an STK Push to a customer's phone number.
     *
     * @param phoneNumber  Customer phone in format 2547XXXXXXXX
     * @param amount       Amount in KES (whole number)
     * @param reference    Account reference (e.g. invoice number)
     * @param onSuccess    Called with the CheckoutRequestID on success
     * @param onError      Called with error message on failure
     */
    public void stkPush(String phoneNumber, int amount, String reference,
                        Consumer<String> onSuccess, Consumer<String> onError) {
        if (!isConfigured()) {
            onError.accept("M-Pesa credentials not configured. Go to Settings → M-Pesa Integration.");
            return;
        }

        new Thread(() -> {
            try {
                String token = getAccessToken();
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                String shortCode = paybillNumber != null && !paybillNumber.isEmpty() ? paybillNumber : tillNumber;
                String password = Base64.getEncoder().encodeToString(
                    (shortCode + passkey + timestamp).getBytes()
                );

                JsonObject payload = new JsonObject();
                payload.addProperty("BusinessShortCode", shortCode);
                payload.addProperty("Password", password);
                payload.addProperty("Timestamp", timestamp);
                payload.addProperty("TransactionType",
                    (paybillNumber != null && !paybillNumber.isEmpty()) ? "CustomerPayBillOnline" : "CustomerBuyGoodsOnline");
                payload.addProperty("Amount", amount);
                payload.addProperty("PartyA", formatPhone(phoneNumber));
                payload.addProperty("PartyB", shortCode);
                payload.addProperty("PhoneNumber", formatPhone(phoneNumber));
                payload.addProperty("CallBackURL", "https://kaziflow.co.ke/mpesa/callback");
                payload.addProperty("AccountReference", reference);
                payload.addProperty("TransactionDesc", "Payment for " + reference);

                RequestBody body = RequestBody.create(
                    gson.toJson(payload),
                    MediaType.parse("application/json")
                );

                Request request = new Request.Builder()
                    .url(baseUrl() + "/mpesa/stkpush/v1/processrequest")
                    .header("Authorization", "Bearer " + token)
                    .post(body)
                    .build();

                try (Response response = http.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    System.out.println("[M-Pesa] STK Push response: " + responseBody);

                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                    if (json.has("CheckoutRequestID")) {
                        String checkoutId = json.get("CheckoutRequestID").getAsString();
                        onSuccess.accept(checkoutId);
                    } else {
                        String errMsg = json.has("errorMessage")
                            ? json.get("errorMessage").getAsString()
                            : "STK push failed. Check credentials.";
                        onError.accept(errMsg);
                    }
                }
            } catch (Exception e) {
                onError.accept("M-Pesa error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Query the status of an STK push using CheckoutRequestID.
     */
    public void queryStatus(String checkoutRequestId,
                            Consumer<String> onSuccess, Consumer<String> onError) {
        if (!isConfigured()) { onError.accept("M-Pesa not configured."); return; }

        new Thread(() -> {
            try {
                String token = getAccessToken();
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                String shortCode = paybillNumber != null && !paybillNumber.isEmpty() ? paybillNumber : tillNumber;
                String password = Base64.getEncoder().encodeToString((shortCode + passkey + timestamp).getBytes());

                JsonObject payload = new JsonObject();
                payload.addProperty("BusinessShortCode", shortCode);
                payload.addProperty("Password", password);
                payload.addProperty("Timestamp", timestamp);
                payload.addProperty("CheckoutRequestID", checkoutRequestId);

                RequestBody body = RequestBody.create(gson.toJson(payload), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                    .url(baseUrl() + "/mpesa/stkpushquery/v1/query")
                    .header("Authorization", "Bearer " + token)
                    .post(body)
                    .build();

                try (Response response = http.newCall(request).execute()) {
                    String resp = response.body().string();
                    JsonObject json = gson.fromJson(resp, JsonObject.class);
                    String code = json.has("ResultCode") ? json.get("ResultCode").getAsString() : "-1";
                    if ("0".equals(code)) {
                        onSuccess.accept("Payment confirmed.");
                    } else {
                        String desc = json.has("ResultDesc") ? json.get("ResultDesc").getAsString() : "Payment not confirmed.";
                        onError.accept(desc);
                    }
                }
            } catch (Exception e) {
                onError.accept("Query error: " + e.getMessage());
            }
        }).start();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Normalizes phone number to 2547XXXXXXXX format.
     */
    private String formatPhone(String phone) {
        if (phone == null) return "";
        phone = phone.replaceAll("[^0-9]", "");
        if (phone.startsWith("0")) phone = "254" + phone.substring(1);
        if (phone.startsWith("+")) phone = phone.substring(1);
        return phone;
    }

    /** Returns a human-readable connection status string. */
    public String getConnectionStatus() {
        if (!isConfigured()) return "Not Configured";
        try {
            getAccessToken();
            return "Connected";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}

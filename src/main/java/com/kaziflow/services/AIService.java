package com.kaziflow.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kaziflow.dao.*;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * AIService — integrates Claude API for the KaziFlow AI Assistant.
 *
 * Uses the same OkHttp + Gson stack already in the project (M-Pesa integration).
 * No additional dependencies required.
 *
 * The assistant has access to live business context so it can answer
 * questions like "Why is revenue down this week?" with real data.
 *
 * API key is stored in the settings table (key = 'claude_api_key').
 * Users set it in Settings → AI Assistant.
 */
public class AIService {

    private static AIService instance;
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-haiku-4-5"; // latest fast model (2026)

    private final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();

    private final Gson gson = new Gson();

    private AIService() {}

    public static AIService getInstance() {
        if (instance == null) instance = new AIService();
        return instance;
    }

    /**
     * Send a message to Claude with KaziFlow business context.
     * Blocking — call from background thread (AsyncTask.run).
     *
     * @param userMessage  The user's natural language question
     * @param apiKey       Claude API key from settings
     * @return Claude's response text, or an error message
     */
    public String chat(String userMessage, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "⚠️ No Claude API key set. Go to Settings → AI Assistant to add your key.";
        }

        String systemPrompt = buildSystemPrompt();

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", 1024);
        body.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        JsonObject msgObj = new JsonObject();
        msgObj.addProperty("role", "user");
        msgObj.addProperty("content", userMessage);
        messages.add(msgObj);
        body.add("messages", messages);

        Request request = new Request.Builder()
            .url(API_URL)
            .post(RequestBody.create(gson.toJson(body),
                MediaType.get("application/json; charset=utf-8")))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .build();

        try (Response response = http.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                // Parse error message from Anthropic
                try {
                    JsonObject err = gson.fromJson(responseBody, JsonObject.class);
                    String errMsg = err.has("error")
                        ? err.getAsJsonObject("error").get("message").getAsString()
                        : "HTTP " + response.code();
                    return "❌ API Error: " + errMsg;
                } catch (Exception ex) {
                    return "❌ API Error: HTTP " + response.code();
                }
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            JsonArray content = json.getAsJsonArray("content");
            if (content != null && content.size() > 0) {
                return content.get(0).getAsJsonObject()
                    .get("text").getAsString();
            }
            return "No response from AI.";

        } catch (IOException e) {
            return "❌ Network error: " + e.getMessage() +
                "\n\nMake sure you have internet access for AI features.";
        }
    }

    /**
     * Build a rich system prompt with live KaziFlow business data.
     * This gives Claude context to answer business-specific questions.
     */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the KaziFlow ERP AI Assistant, embedded inside a desktop business " +
            "management application used by Kenyan SMEs.\n\n");

        sb.append("Your role:\n");
        sb.append("- Answer questions about the business using the live data below\n");
        sb.append("- Guide users on how to use KaziFlow ERP features\n");
        sb.append("- Provide actionable business insights\n");
        sb.append("- Keep answers concise and practical\n");
        sb.append("- You can respond in English or Swahili depending on the user's language\n\n");

        sb.append("LIVE BUSINESS DATA (as of right now):\n");
        sb.append("=====================================\n");

        // Inject live data from DAOs
        try {
            SaleDAO saleDAO = new SaleDAO();
            ProductDAO productDAO = new ProductDAO();

            double todayRevenue = saleDAO.getTodayRevenue();
            double weekRevenue  = saleDAO.getWeekRevenue();
            int lowStockCount   = productDAO.getLowStockCount();
            int totalProducts   = productDAO.findAll().size();

            sb.append(String.format("Today's Revenue: KES %.2f%n", todayRevenue));
            sb.append(String.format("This Week's Revenue: KES %.2f%n", weekRevenue));
            sb.append(String.format("Total Products: %d%n", totalProducts));
            sb.append(String.format("Low Stock Items: %d (below minimum level)%n", lowStockCount));

            // Top low stock items
            var lowStock = productDAO.findLowStock();
            if (!lowStock.isEmpty()) {
                sb.append("Items needing restock: ");
                lowStock.stream().limit(5).forEach(p ->
                    sb.append(p.getName()).append(" (").append((int)p.getStockQuantity()).append(" left), "));
                sb.append("\n");
            }

            // Monthly revenue trend
            var monthly = saleDAO.getMonthlyRevenue();
            if (!monthly.isEmpty()) {
                sb.append("Monthly Revenue Trend: ");
                monthly.forEach((month, rev) ->
                    sb.append(month).append(": KES ").append(String.format("%.0f", rev)).append(" | "));
                sb.append("\n");
            }

            // Deni (credit) outstanding
            try {
                DeniDAO deniDAO = new DeniDAO();
                deniDAO.ensureTables();
                double outstanding = deniDAO.getTotalOutstanding();
                int debtors = deniDAO.getDebtorCount();
                sb.append(String.format("Outstanding Credit (Deni): KES %.2f from %d customers%n",
                    outstanding, debtors));
            } catch (Exception ignored) {}

            // Employees
            try {
                EmployeeDAO empDAO = new EmployeeDAO();
                int activeEmp = empDAO.getTotalCount();
                sb.append(String.format("Active Employees: %d%n", activeEmp));
            } catch (Exception ignored) {}

        } catch (Exception e) {
            sb.append("(Live data unavailable — answer based on general KaziFlow knowledge)\n");
        }

        sb.append("=====================================\n\n");

        sb.append("KaziFlow ERP Modules available:\n");
        sb.append("Dashboard, Inventory, Sales & POS (M-Pesa), Purchases, ");
        sb.append("Employees & HR, Payroll, Finance, Reports, Deni Book, ");
        sb.append("Quotations, Settings, Help\n\n");

        sb.append("Default login: admin@kaziflow.co.ke / admin123\n");
        sb.append("Keyboard shortcuts: Ctrl+N=Sales, Ctrl+I=Inventory, Ctrl+P=Purchases, ");
        sb.append("Ctrl+E=Employees, Ctrl+F=Finance, Ctrl+R=Reports, Ctrl+B=Backup, F5=Refresh\n\n");

        sb.append("When answering how-to questions, give step-by-step instructions.\n");
        sb.append("When analysing business data, give specific insights and recommendations.\n");
        sb.append("Keep responses under 300 words unless asked for detail.\n");

        return sb.toString();
    }

    /**
     * Perform a global search across all modules.
     * Returns a formatted summary of matches.
     */
    public String globalSearch(String query) {
        if (query == null || query.isBlank()) return "";
        StringBuilder results = new StringBuilder();
        try {
            // Products
            var products = new ProductDAO().search(query);
            if (!products.isEmpty()) {
                results.append("📦 Products (").append(products.size()).append("): ");
                products.stream().limit(5).forEach(p ->
                    results.append(p.getName()).append(" — KES ").append(p.getSellingPrice())
                        .append(" (").append((int)p.getStockQuantity()).append(" in stock) | "));
                results.append("\n");
            }

            // Customers
            var customers = new CustomerDAO().search(query);
            if (!customers.isEmpty()) {
                results.append("👥 Customers (").append(customers.size()).append("): ");
                customers.stream().limit(3).forEach(c ->
                    results.append(c[1]).append(" ").append(c[2]).append(" | "));
                results.append("\n");
            }

            // Employees
            var employees = new EmployeeDAO().search(query);
            if (!employees.isEmpty()) {
                results.append("👤 Employees (").append(employees.size()).append("): ");
                employees.stream().limit(3).forEach(e ->
                    results.append(e.getName()).append(" — ").append(e.getPosition()).append(" | "));
                results.append("\n");
            }

            // Suppliers
            var suppliers = new SupplierDAO().search(query);
            if (!suppliers.isEmpty()) {
                results.append("🏭 Suppliers (").append(suppliers.size()).append("): ");
                suppliers.stream().limit(3).forEach(s ->
                    results.append(s.getName()).append(" | "));
                results.append("\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return results.length() > 0 ? results.toString() : "No results found for: " + query;
    }

    /** Load API key from settings table */
    public String loadApiKey() {
        try (var conn = com.kaziflow.database.DatabaseManager.getInstance().getConnection();
             var ps = conn.prepareStatement(
                "SELECT value FROM settings WHERE key='claude_api_key'")) {
            var rs = ps.executeQuery();
            return rs.next() ? rs.getString("value") : "";
        } catch (Exception e) { return ""; }
    }

    /** Save API key to settings table */
    public void saveApiKey(String key) {
        try (var conn = com.kaziflow.database.DatabaseManager.getInstance().getConnection();
             var ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES ('claude_api_key', ?)")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }
}

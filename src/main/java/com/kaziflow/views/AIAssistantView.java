package com.kaziflow.views;

import com.kaziflow.services.AIService;
import com.kaziflow.utils.AsyncTask;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * AIAssistantView — floating AI chat panel.
 *
 * Appears as a slide-in panel from the right edge of the screen.
 * Shows/hides via the ✦ AI button in the top-right of MainLayout.
 *
 * Features:
 *   - Chat with Claude API using live business context
 *   - Global search across all modules
 *   - Quick-action suggestion chips
 *   - Conversation history preserved during session
 *   - API key management built-in
 */
public class AIAssistantView {

    private VBox panel;
    private VBox chatBox;
    private TextField inputField;
    private Button sendBtn;
    private final AIService aiService = AIService.getInstance();
    private final List<String[]> conversationHistory = new ArrayList<>(); // [role, content]
    private boolean isVisible = false;

    public AIAssistantView() {
        buildPanel();
    }

    public VBox getPanel() { return panel; }
    public boolean isVisible() { return isVisible; }

    public void show() {
        isVisible = true;
        panel.setVisible(true);
        panel.setManaged(true);
        inputField.requestFocus();
        // Show welcome if empty
        if (chatBox.getChildren().isEmpty()) showWelcome();
    }

    public void hide() {
        isVisible = false;
        panel.setVisible(false);
        panel.setManaged(false);
    }

    public void toggle() {
        if (isVisible) hide(); else show();
    }

    // ── Panel Build ────────────────────────────────────────────────────────

    private void buildPanel() {
        panel = new VBox(0);
        panel.setPrefWidth(380);
        panel.setMaxWidth(380);
        panel.setMinWidth(380);
        panel.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #e2e8f0 transparent transparent #e2e8f0;" +
            "-fx-border-width: 0 0 0 1;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 12, 0, -4, 0);"
        );
        panel.setVisible(false);
        panel.setManaged(false);

        panel.getChildren().addAll(buildPanelHeader(), buildChatArea(), buildInputArea());
    }

    private HBox buildPanelHeader() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 14, 16));
        header.setStyle(
            "-fx-background-color: #0d1b2a;" +
            "-fx-border-color: transparent transparent #1e2d3d transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        Label icon = new Label("✦");
        icon.setStyle("-fx-text-fill: #02a870; -fx-font-size: 16px;");

        VBox titleBlock = new VBox(1);
        Label title = new Label("KaziFlow AI");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        Label sub = new Label("Powered by Claude · Live business data");
        sub.setStyle("-fx-text-fill: #64748b; -fx-font-size: 10px;");
        titleBlock.getChildren().addAll(title, sub);

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button clearBtn = new Button("🗑");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; " +
            "-fx-cursor: hand; -fx-font-size: 13px;");
        clearBtn.setTooltip(new Tooltip("Clear conversation"));
        clearBtn.setOnAction(e -> {
            chatBox.getChildren().clear();
            conversationHistory.clear();
            showWelcome();
        });

        Button settingsBtn = new Button("⚙");
        settingsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; " +
            "-fx-cursor: hand; -fx-font-size: 13px;");
        settingsBtn.setTooltip(new Tooltip("Configure API key"));
        settingsBtn.setOnAction(e -> showApiKeyDialog());

        header.getChildren().addAll(icon, titleBlock, sp, clearBtn, settingsBtn);
        return header;
    }

    private ScrollPane buildChatArea() {
        chatBox = new VBox(12);
        chatBox.setPadding(new Insets(16));
        chatBox.setStyle("-fx-background-color: #f8fafc;");

        ScrollPane scroll = new ScrollPane(chatBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #f8fafc; -fx-background: #f8fafc;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Auto-scroll to bottom
        chatBox.heightProperty().addListener((obs, old, nw) ->
            scroll.setVvalue(1.0));

        return scroll;
    }

    private VBox buildInputArea() {
        VBox inputArea = new VBox(8);
        inputArea.setPadding(new Insets(12, 16, 12, 16));
        inputArea.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #e2e8f0 transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;"
        );

        // Quick suggestion chips
        FlowPane chips = new FlowPane(6, 4);
        chips.setPrefWrapLength(348);
        String[] suggestions = {
            "Today's revenue?",
            "Low stock items",
            "Add a product",
            "How to run payroll?",
            "Who owes me money?"
        };
        for (String s : suggestions) {
            Button chip = new Button(s);
            chip.setStyle(
                "-fx-background-color: #f1f5f9; -fx-text-fill: #475569;" +
                "-fx-border-color: #e2e8f0; -fx-border-radius: 20; -fx-background-radius: 20;" +
                "-fx-font-size: 10px; -fx-padding: 4 10; -fx-cursor: hand;"
            );
            chip.setOnAction(e -> {
                inputField.setText(s);
                sendMessage();
            });
            chips.getChildren().add(chip);
        }

        // Input row
        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER);

        inputField = new TextField();
        inputField.setPromptText("Ask anything about your business...");
        inputField.setStyle(
            "-fx-pref-height: 38px; -fx-background-color: #f8fafc;" +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 20; -fx-background-radius: 20;" +
            "-fx-font-size: 13px; -fx-padding: 0 14;"
        );
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> sendMessage());

        sendBtn = new Button("↑");
        sendBtn.setStyle(
            "-fx-background-color: #02a870; -fx-text-fill: white; -fx-font-weight: bold;" +
            "-fx-background-radius: 50; -fx-min-width: 38px; -fx-min-height: 38px;" +
            "-fx-font-size: 16px; -fx-cursor: hand;"
        );
        sendBtn.setOnAction(e -> sendMessage());

        // Global search button
        Button searchBtn = new Button("🔍");
        searchBtn.setStyle(
            "-fx-background-color: #2563eb; -fx-text-fill: white;" +
            "-fx-background-radius: 50; -fx-min-width: 38px; -fx-min-height: 38px;" +
            "-fx-font-size: 14px; -fx-cursor: hand;"
        );
        searchBtn.setTooltip(new Tooltip("Global search across all modules"));
        searchBtn.setOnAction(e -> globalSearch());

        inputRow.getChildren().addAll(inputField, searchBtn, sendBtn);
        inputArea.getChildren().addAll(chips, inputRow);
        return inputArea;
    }

    // ── Welcome Screen ────────────────────────────────────────────────────

    private void showWelcome() {
        VBox welcome = new VBox(12);
        welcome.setAlignment(Pos.CENTER);
        welcome.setPadding(new Insets(20));

        Label icon = new Label("✦");
        icon.setStyle("-fx-font-size: 36px; -fx-text-fill: #02a870;");

        Label title = new Label("KaziFlow AI Assistant");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label desc = new Label("I have access to your live business data.\nAsk me anything!");
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-text-alignment: center;");
        desc.setWrapText(true);
        desc.setMaxWidth(300);

        // Example questions
        VBox examples = new VBox(6);
        examples.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; " +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-padding: 12;");
        Label examplesLbl = new Label("Try asking:");
        examplesLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        String[] examples_ = {
            "\"Ninauzaje bidhaa kwa credit?\"",
            "\"Which products are running low?\"",
            "\"How do I process a return?\"",
            "\"What's my profit this month?\"",
            "\"Guide me through adding an employee\""
        };
        examples.getChildren().add(examplesLbl);
        for (String ex : examples_) {
            Label lbl = new Label("• " + ex);
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
            lbl.setWrapText(true);
            examples.getChildren().add(lbl);
        }

        welcome.getChildren().addAll(icon, title, desc, examples);
        chatBox.getChildren().add(welcome);
    }

    // ── Message Handling ──────────────────────────────────────────────────

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();

        addUserBubble(text);
        conversationHistory.add(new String[]{"user", text});

        sendBtn.setDisable(true);
        Label typing = new Label("✦ Thinking...");
        typing.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-font-style: italic;");
        chatBox.getChildren().add(typing);

        String apiKey = aiService.loadApiKey();

        AsyncTask.run(
            () -> aiService.chat(text, apiKey),
            response -> {
                chatBox.getChildren().remove(typing);
                addAssistantBubble(response);
                conversationHistory.add(new String[]{"assistant", response});
                sendBtn.setDisable(false);
            },
            err -> {
                chatBox.getChildren().remove(typing);
                addAssistantBubble("❌ Error: " + err);
                sendBtn.setDisable(false);
            }
        );
    }

    private void globalSearch() {
        String query = inputField.getText().trim();
        if (query.isEmpty()) {
            addAssistantBubble("Type something in the search box first, then click 🔍");
            return;
        }
        inputField.clear();

        addUserBubble("🔍 Search: " + query);

        Label searching = new Label("Searching all modules...");
        searching.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        chatBox.getChildren().add(searching);

        AsyncTask.run(
            () -> aiService.globalSearch(query),
            results -> {
                chatBox.getChildren().remove(searching);
                addAssistantBubble("**Search results for: " + query + "**\n\n" + results);
            },
            err -> {
                chatBox.getChildren().remove(searching);
                addAssistantBubble("❌ Search error: " + err);
            }
        );
    }

    // ── Bubble Rendering ──────────────────────────────────────────────────

    private void addUserBubble(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(280);
        bubble.setStyle(
            "-fx-background-color: #2563eb; -fx-text-fill: white;" +
            "-fx-background-radius: 18 18 4 18; -fx-padding: 10 14; -fx-font-size: 13px;"
        );
        row.getChildren().add(bubble);
        chatBox.getChildren().add(row);
    }

    private void addAssistantBubble(String text) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.TOP_LEFT);

        Label avatarLbl = new Label("✦");
        avatarLbl.setStyle("-fx-text-fill: #02a870; -fx-font-size: 14px; -fx-padding: 6 0 0 0;");

        // Render markdown-style bold (**text**)
        Label bubble = new Label(text.replace("**", ""));
        bubble.setWrapText(true);
        bubble.setMaxWidth(290);
        bubble.setFont(Font.font("Arial", 13));
        bubble.setStyle(
            "-fx-background-color: white; -fx-text-fill: #1e293b;" +
            "-fx-background-radius: 4 18 18 18; -fx-padding: 10 14;" +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 4 18 18 18;"
        );

        // Copy button
        Button copyBtn = new Button("📋");
        copyBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 11px;");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(text);
            cb.setContent(cc);
        });

        VBox content = new VBox(4, bubble, copyBtn);
        row.getChildren().addAll(avatarLbl, content);
        chatBox.getChildren().add(row);
    }

    // ── API Key Dialog ─────────────────────────────────────────────────────

    private void showApiKeyDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("AI Assistant — API Key");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.getDialogPane().setPrefWidth(440);

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        Label info = new Label(
            "KaziFlow AI uses Claude by Anthropic.\n" +
            "Get a free API key at: console.anthropic.com\n\n" +
            "Your key is stored locally on this device only.\n" +
            "It is never sent anywhere except directly to Anthropic's API."
        );
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");

        String existing = aiService.loadApiKey();
        PasswordField keyField = new PasswordField();
        keyField.setText(existing);
        keyField.setPromptText("sk-ant-...");
        keyField.setStyle(
            "-fx-pref-height: 36px; -fx-background-color: white;" +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-font-size: 13px; -fx-padding: 0 10;"
        );

        Label hint = new Label("Key starts with: sk-ant-api03-...");
        hint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        content.getChildren().addAll(info, keyField, hint);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                aiService.saveApiKey(keyField.getText().trim());
                addAssistantBubble("✅ API key saved. I'm ready to help! Try asking me something.");
            }
        });
    }
}

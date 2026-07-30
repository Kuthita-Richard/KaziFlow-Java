package com.kaziflow.utils;

import com.kaziflow.models.User;

public class SessionManager {

    private static SessionManager instance;
    private User currentUser;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) instance = new SessionManager();
        return instance;
    }

    public void login(User user) {
        this.currentUser = user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean hasPermission(String permission) {
        if (currentUser == null) return false;
        String perms = currentUser.getPermissions();
        return perms != null && (perms.contains("\"all\": true") || perms.contains("\"" + permission + "\": true"));
    }

    public String getBusinessSetting(String key) {
        return ""; // Will be fetched from DB
    }
}

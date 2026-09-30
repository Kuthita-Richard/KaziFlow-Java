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
        // Permissions are resolved per user and cached; a new session must never inherit the last one's.
        com.kaziflow.services.PermissionService.getInstance().invalidate();
    }

    public void logout() {
        this.currentUser = null;
        com.kaziflow.services.PermissionService.getInstance().invalidate();
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * @deprecated The old substring match against the legacy roles JSON had zero callers and did not
     * reflect Positions. Delegates to the real engine so nothing can accidentally rely on the old
     * behaviour; new code should call PermissionService directly (can(moduleId, action) / canPerform(actionKey)).
     */
    @Deprecated
    public boolean hasPermission(String permission) {
        return currentUser != null && com.kaziflow.services.PermissionService.getInstance().canPerform(permission);
    }

    public String getBusinessSetting(String key) {
        return ""; // Will be fetched from DB
    }
}

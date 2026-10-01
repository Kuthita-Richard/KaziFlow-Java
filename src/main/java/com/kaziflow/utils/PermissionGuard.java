package com.kaziflow.utils;

import com.kaziflow.services.PermissionService;
import com.kaziflow.services.PermissionService.Action;
import javafx.scene.Node;

/**
 * PermissionGuard — the ONE way a screen reflects a permission on a control, so
 * individual views never hand-roll "if admin ... else disable" logic (the same idea
 * as DialogUtils / PasswordFieldUtils). Thin on purpose: it only asks the two generic
 * {@link PermissionService} checks, so it works unchanged for any module or action key.
 *
 * <pre>
 *   returnBtn = PermissionGuard.requireAction(returnBtn, SpecialPermissions.VOID_SALE);
 *   saveBtn   = PermissionGuard.requireModule(saveBtn, "inventory", Action.WRITE);
 * </pre>
 *
 * This is presentation only (greying a control out). Real enforcement of navigation lives in
 * SceneManager.navigateTo(); a destructive code path should ALSO re-check {@link #allowsAction}.
 */
public final class PermissionGuard {

    private PermissionGuard() {}

    public static boolean allowsAction(String actionKey) {
        return PermissionService.getInstance().canPerform(actionKey);
    }

    public static boolean allowsModule(String moduleId, Action action) {
        return PermissionService.getInstance().can(moduleId, action);
    }

    /** Disables {@code node} (and dims it) unless the user holds the named special action. Returns the node for chaining. */
    public static <T extends Node> T requireAction(T node, String actionKey) {
        return apply(node, allowsAction(actionKey));
    }

    /** Disables {@code node} unless the user holds {@code action} on {@code moduleId}. */
    public static <T extends Node> T requireModule(T node, String moduleId, Action action) {
        return apply(node, allowsModule(moduleId, action));
    }

    private static <T extends Node> T apply(T node, boolean allowed) {
        if (!allowed) {
            node.setDisable(true);
            node.setOpacity(0.45);
        }
        return node;
    }
}

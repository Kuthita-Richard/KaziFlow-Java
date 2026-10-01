package com.kaziflow.custom;

import com.kaziflow.custom.CustomModuleService.ModuleDef;
import com.kaziflow.modules.Module;
import com.kaziflow.modules.ModuleRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CustomModuleLoader — keeps {@link ModuleRegistry} in step with the {@code custom_modules} table:
 * active modules are registered, deactivated/deleted ones are removed, and a changed label or icon is
 * re-registered. Called at startup and after the builder saves anything.
 */
public final class CustomModuleLoader {

    private CustomModuleLoader() {}

    /** @return true if the set of registered custom modules (or any label/icon) changed. */
    public static boolean sync() {
        ModuleRegistry reg = ModuleRegistry.getInstance();
        List<ModuleDef> wanted = CustomModuleService.getInstance().listModules(true);
        Map<String, ModuleDef> byId = new HashMap<>();
        for (ModuleDef d : wanted) byId.put(d.moduleId(), d);

        boolean changed = false;
        for (Module m : List.copyOf(reg.getAllAvailableModules())) {
            if (!m.isUserDefined()) continue;
            ModuleDef d = byId.get(m.getId());
            boolean stale = d == null
                || !(m instanceof CustomModule cm)
                || !cm.getDef().label().equals(d.label()) || !cm.getDef().icon().equals(d.icon());
            if (stale) { reg.unregisterModule(m.getId()); changed = true; }
        }
        for (ModuleDef d : wanted) {
            if (reg.registerModule(new CustomModule(d))) changed = true;
        }
        return changed;
    }
}

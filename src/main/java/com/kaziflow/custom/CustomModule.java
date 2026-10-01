package com.kaziflow.custom;

import com.kaziflow.custom.CustomModuleService.ModuleDef;
import com.kaziflow.modules.Module;
import javafx.scene.layout.Region;

/**
 * CustomModule — the single {@link Module} implementation behind EVERY admin-defined department.
 * It carries no behaviour of its own: its id, label and icon come from the {@code custom_modules} row and
 * its screen is {@link CustomModuleView}, generated from the module's entity/field definitions. That is
 * what makes "add an Ambulance department" a data operation rather than a code change.
 *
 * <p>Because it is {@link #isUserDefined()}, it is always active once registered, and — like every module —
 * it is default-deny until a Position grants it (the Positions editor lists it automatically).
 */
public final class CustomModule implements Module {

    private final ModuleDef def;

    public CustomModule(ModuleDef def) { this.def = def; }

    public ModuleDef getDef() { return def; }

    @Override public String getId() { return def.moduleId(); }
    @Override public String getLabel() { return def.label(); }
    @Override public String getIcon() { return def.icon(); }
    @Override public boolean isUserDefined() { return true; }
    /** Rebuilt on every visit so changes made in the builder show immediately. */
    @Override public boolean noCache() { return true; }
    @Override public Region buildView() { return new CustomModuleView(def.id()).getRoot(); }
}

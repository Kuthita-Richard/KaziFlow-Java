package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.InventoryView;
import javafx.scene.layout.Region;

public class InventoryModule implements Module {
    @Override public String getId()    { return "inventory"; }
    @Override public String getIcon()  { return "◈"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.inventory"); }
    @Override public Region buildView() { return new InventoryView().getRoot(); }
}

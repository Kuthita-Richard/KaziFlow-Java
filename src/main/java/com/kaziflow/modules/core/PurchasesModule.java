package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.PurchasesView;
import javafx.scene.layout.Region;

public class PurchasesModule implements Module {
    @Override public String getId()    { return "purchases"; }
    @Override public String getIcon()  { return "◉"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.purchases"); }
    @Override public Region buildView() { return new PurchasesView().getRoot(); }
}

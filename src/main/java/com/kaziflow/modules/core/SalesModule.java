package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.SalesView;
import javafx.scene.layout.Region;

public class SalesModule implements Module {
    @Override public String getId()    { return "sales"; }
    @Override public String getIcon()  { return "⊙"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.sales"); }
    @Override public Region buildView() { return new SalesView().getRoot(); }
}

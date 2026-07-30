package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.DashboardView;
import javafx.scene.layout.Region;

public class DashboardModule implements Module {
    @Override public String getId()    { return "dashboard"; }
    @Override public String getIcon()  { return "⊞"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.dashboard"); }
    @Override public Region buildView() { return new DashboardView().getRoot(); }
}

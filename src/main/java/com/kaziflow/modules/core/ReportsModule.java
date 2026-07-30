package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.ReportsView;
import javafx.scene.layout.Region;

public class ReportsModule implements Module {
    @Override public String getId()    { return "reports"; }
    @Override public String getIcon()  { return "▦"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.reports"); }
    @Override public Region buildView() { return new ReportsView().getRoot(); }
}

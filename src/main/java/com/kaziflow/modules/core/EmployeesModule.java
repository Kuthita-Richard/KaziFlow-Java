package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.EmployeesView;
import javafx.scene.layout.Region;

public class EmployeesModule implements Module {
    @Override public String getId()    { return "employees"; }
    @Override public String getIcon()  { return "◎"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.employees"); }
    @Override public Region buildView() { return new EmployeesView().getRoot(); }
}

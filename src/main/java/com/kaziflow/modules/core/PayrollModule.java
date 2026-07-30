package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.PayrollView;
import javafx.scene.layout.Region;

public class PayrollModule implements Module {
    @Override public String getId()    { return "payroll"; }
    @Override public String getIcon()  { return "💵"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.payroll"); }
    @Override public Region buildView(){ return new PayrollView().getRoot(); }
}

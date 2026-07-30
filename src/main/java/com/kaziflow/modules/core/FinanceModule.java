package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.FinanceView;
import javafx.scene.layout.Region;

public class FinanceModule implements Module {
    @Override public String getId()    { return "finance"; }
    @Override public String getIcon()  { return "◆"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.finance"); }
    @Override public Region buildView() { return new FinanceView().getRoot(); }
}

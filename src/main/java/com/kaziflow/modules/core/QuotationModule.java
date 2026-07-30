package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.QuotationView;
import javafx.scene.layout.Region;

public class QuotationModule implements Module {
    @Override public String getId()    { return "quotations"; }
    @Override public String getIcon()  { return "📋"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.quotations"); }
    @Override public Region buildView(){ return new QuotationView().getRoot(); }
}

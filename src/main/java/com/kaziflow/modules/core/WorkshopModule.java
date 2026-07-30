package com.kaziflow.modules.core;
import com.kaziflow.modules.Module;
import com.kaziflow.views.WorkshopView;
import javafx.scene.layout.Region;
public class WorkshopModule implements Module {
    @Override public String getId()    { return "workshop"; }
    @Override public String getIcon()  { return "🔧"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.workshop"); }
    @Override public Region buildView(){ return new WorkshopView().getRoot(); }
}

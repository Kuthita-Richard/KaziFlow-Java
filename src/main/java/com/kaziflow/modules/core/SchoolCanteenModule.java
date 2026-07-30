package com.kaziflow.modules.core;
import com.kaziflow.modules.Module;
import com.kaziflow.views.SchoolCanteenView;
import javafx.scene.layout.Region;
public class SchoolCanteenModule implements Module {
    @Override public String getId()    { return "canteen"; }
    @Override public String getIcon()  { return "🏫"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.canteen"); }
    @Override public Region buildView(){ return new SchoolCanteenView().getRoot(); }
}

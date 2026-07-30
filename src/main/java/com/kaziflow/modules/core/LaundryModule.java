package com.kaziflow.modules.core;
import com.kaziflow.modules.Module;
import com.kaziflow.views.LaundryView;
import javafx.scene.layout.Region;
public class LaundryModule implements Module {
    @Override public String getId()    { return "laundry"; }
    @Override public String getIcon()  { return "👔"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.laundry"); }
    @Override public Region buildView(){ return new LaundryView().getRoot(); }
}

package com.kaziflow.modules.core;
import com.kaziflow.modules.Module;
import com.kaziflow.views.HotelView;
import javafx.scene.layout.Region;
public class HotelModule implements Module {
    @Override public String getId()    { return "hotel"; }
    @Override public String getIcon()  { return "🏨"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.hotel"); }
    @Override public Region buildView(){ return new HotelView().getRoot(); }
}

package com.kaziflow.modules.core;
import com.kaziflow.modules.Module;
import com.kaziflow.views.FuelStationView;
import javafx.scene.layout.Region;
public class FuelStationModule implements Module {
    @Override public String getId()    { return "fuel_station"; }
    @Override public String getIcon()  { return "⛽"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.fuel"); }
    @Override public Region buildView(){ return new FuelStationView().getRoot(); }
}

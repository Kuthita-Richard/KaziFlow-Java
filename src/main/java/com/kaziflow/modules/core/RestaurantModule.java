package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.RestaurantView;
import javafx.scene.layout.Region;

public class RestaurantModule implements Module {
    @Override public String getId()    { return "restaurant"; }
    @Override public String getIcon()  { return "🍽"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.restaurant"); }
    @Override public Region buildView(){ return new RestaurantView().getRoot(); }
}

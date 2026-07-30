package com.kaziflow.modules.core;

import com.kaziflow.dao.DeliveryDAO;
import com.kaziflow.modules.Module;
import com.kaziflow.views.DeliveryView;
import javafx.scene.layout.Region;

public class DeliveryModule implements Module {
    @Override public String getId()    { return "delivery"; }
    @Override public String getIcon()  { return "🚚"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.delivery"); }
    @Override public Region buildView(){ return new DeliveryView().getRoot(); }
    @Override public void onLoad()     { new DeliveryDAO().ensureTables(); }
}

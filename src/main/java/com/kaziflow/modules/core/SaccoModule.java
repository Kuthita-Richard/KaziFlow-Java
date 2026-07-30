package com.kaziflow.modules.core;

import com.kaziflow.dao.SaccoDAO;
import com.kaziflow.modules.Module;
import com.kaziflow.views.SaccoView;
import javafx.scene.layout.Region;

public class SaccoModule implements Module {
    @Override public String getId()    { return "sacco"; }
    @Override public String getIcon()  { return "🤝"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.sacco"); }
    @Override public Region buildView(){ return new SaccoView().getRoot(); }
    @Override public void onLoad()     { new SaccoDAO().ensureTables(); }
}

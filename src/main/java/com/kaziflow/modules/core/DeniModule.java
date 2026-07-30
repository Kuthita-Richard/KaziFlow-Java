package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.DeniView;
import javafx.scene.layout.Region;

public class DeniModule implements Module {
    @Override public String getId()    { return "deni"; }
    @Override public String getIcon()  { return "📒"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.deni"); }
    @Override public Region buildView(){ return new DeniView().getRoot(); }
}

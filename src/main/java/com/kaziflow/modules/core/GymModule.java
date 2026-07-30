package com.kaziflow.modules.core;
import com.kaziflow.modules.Module;
import com.kaziflow.views.GymView;
import javafx.scene.layout.Region;
public class GymModule implements Module {
    @Override public String getId()    { return "gym"; }
    @Override public String getIcon()  { return "💪"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.gym"); }
    @Override public Region buildView(){ return new GymView().getRoot(); }
}

package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.SettingsView;
import javafx.scene.layout.Region;

public class SettingsModule implements Module {
    @Override public String getId()       { return "settings"; }
    @Override public String getIcon()     { return "⚙"; }
    @Override public String getLabel()    { return com.kaziflow.utils.I18n.t("nav.settings"); }
    @Override public boolean noCache()    { return true; }
    @Override public boolean isBottomNav(){ return true; }
    @Override public Region buildView()   { return new SettingsView().getRoot(); }
}

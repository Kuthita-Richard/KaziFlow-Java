package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.HelpView;
import javafx.scene.layout.Region;

public class HelpModule implements Module {
    @Override public String getId()       { return "help"; }
    @Override public String getIcon()     { return "?"; }
    @Override public String getLabel()    { return com.kaziflow.utils.I18n.t("nav.help"); }
    @Override public boolean noCache()    { return true; }
    @Override public boolean isBottomNav(){ return true; }
    @Override public Region buildView()   { return new HelpView().getRoot(); }
}

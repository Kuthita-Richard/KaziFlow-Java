package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.AppointmentView;
import javafx.scene.layout.Region;

public class AppointmentModule implements Module {
    @Override public String getId()    { return "appointments"; }
    @Override public String getIcon()  { return "📅"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.appointments"); }
    @Override public Region buildView(){ return new AppointmentView().getRoot(); }
}

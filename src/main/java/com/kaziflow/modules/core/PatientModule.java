package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.PatientView;
import javafx.scene.layout.Region;

public class PatientModule implements Module {
    @Override public String getId()    { return "patients"; }
    @Override public String getIcon()  { return "🏥"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.patients"); }
    @Override public Region buildView(){ return new PatientView().getRoot(); }
}

package com.kaziflow.modules.core;

import com.kaziflow.modules.Module;
import com.kaziflow.views.BatchView;
import javafx.scene.layout.Region;

public class BatchModule implements Module {
    @Override public String getId()    { return "batches"; }
    @Override public String getIcon()  { return "📦"; }
    @Override public String getLabel() { return com.kaziflow.utils.I18n.t("nav.batches"); }
    @Override public Region buildView(){ return new BatchView().getRoot(); }
}

module com.kaziflow {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires java.sql;
    requires java.desktop;
    requires java.prefs;
    requires org.xerial.sqlitejdbc;
    requires jbcrypt;
    requires okhttp3;
    requires com.google.gson;

    opens com.kaziflow to javafx.fxml;
    opens com.kaziflow.views to javafx.fxml;
    opens com.kaziflow.models to javafx.base;

    exports com.kaziflow;
    exports com.kaziflow.views;
    exports com.kaziflow.models;
    exports com.kaziflow.dao;
    exports com.kaziflow.utils;
    exports com.kaziflow.services;
    exports com.kaziflow.modules;
    exports com.kaziflow.modules.core;
    exports com.kaziflow.security;
    exports com.kaziflow.license;
}

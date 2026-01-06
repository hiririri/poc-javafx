module com.csvmonitor {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires org.drombler.commons.docking.fx;
    requires org.drombler.commons.docking.core;
    requires org.slf4j;
    requires javafx.base;
    requires java.prefs;

    opens com.csvmonitor.view to javafx.fxml;
    opens com.csvmonitor.model to javafx.base;
    
    exports com.csvmonitor.view;
    exports com.csvmonitor.model;
    exports com.csvmonitor.viewmodel;
}

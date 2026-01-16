package com.natixis.etrading.gui.swing.view;

import java.io.File;

public interface MainView {
    CsvTableModel getTableModel();

    void setStatus(String message);

    void setStartPauseLabel(String text);

    void updateRowCount();

    File promptOpenCsv();

    File promptSaveCsv();

    void addWindowCloseHandler(Runnable handler);
}

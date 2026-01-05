package com.csvmonitor.swing.controller;

import com.csvmonitor.swing.model.CsvRepository;
import com.csvmonitor.swing.model.RowData;
import com.csvmonitor.swing.model.UpdateEngine;
import com.csvmonitor.swing.view.CsvTableModel;
import com.csvmonitor.swing.view.MainView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private final MainView view;
    private final CsvRepository csvRepository;
    private final UpdateEngine updateEngine;
    private boolean updateRunning;

    public MainController(MainView view, CsvRepository csvRepository, UpdateEngine updateEngine) {
        this.view = view;
        this.csvRepository = csvRepository;
        this.updateEngine = updateEngine;
    }

    public void onViewReady() {
        setupUpdateEngine();
        view.addWindowCloseHandler(this::shutdown);
        loadDefaultCsv();
    }

    public void onOpenCsv() {
        File file = view.promptOpenCsv();
        if (file != null) {
            loadCsvFromFile(file);
        }
    }

    public void onSaveCsv() {
        File file = view.promptSaveCsv();
        if (file != null) {
            saveCsvToFile(file);
        }
    }

    public void onStartPause() {
        if (updateRunning) {
            pauseUpdates();
        } else {
            startUpdates();
        }
    }

    public void onUnlockAll() {
        view.getTableModel().getData().forEach(RowData::unlock);
        view.setStatus("All rows unlocked");
        logger.info("All rows unlocked");
    }

    private void setupUpdateEngine() {
        updateEngine.setTableUpdateCallback(() -> view.getTableModel().fireAllDataUpdated());
    }

    private void loadDefaultCsv() {
        logger.info("Loading default CSV...");
        view.setStatus("Loading default CSV...");

        CompletableFuture.runAsync(() -> {
            List<RowData> data = csvRepository.loadDefaultCsv();
            SwingUtilities.invokeLater(() -> applyLoadedData(data, "sample.csv"));
        }).exceptionally(ex -> {
            logger.error("Failed to load default CSV", ex);
            SwingUtilities.invokeLater(() -> view.setStatus("Failed to load CSV: " + ex.getMessage()));
            return null;
        });
    }

    private void loadCsvFromFile(File file) {
        logger.info("Loading CSV from file: {}", file.getName());
        view.setStatus("Loading " + file.getName() + "...");

        boolean wasRunning = updateRunning;
        if (wasRunning) {
            pauseUpdates();
        }

        String fileName = file.getName();

        CompletableFuture.runAsync(() -> {
            List<RowData> data = csvRepository.loadCsvFromFile(file);
            SwingUtilities.invokeLater(() -> {
                applyLoadedData(data, fileName);
                if (wasRunning) {
                    startUpdates();
                }
            });
        }).exceptionally(ex -> {
            logger.error("Failed to load CSV from file: {}", fileName, ex);
            SwingUtilities.invokeLater(() -> {
                if (wasRunning) {
                    startUpdates();
                }
                view.setStatus("Failed to load " + fileName + ": " + ex.getMessage());
            });
            return null;
        });
    }

    private void applyLoadedData(List<RowData> data, String sourceName) {
        CsvTableModel tableModel = view.getTableModel();
        tableModel.setData(data);
        updateEngine.setData(tableModel.getData());
        view.updateRowCount();
        view.setStatus("Loaded %d rows from %s".formatted(data.size(), sourceName));
        logger.info("Loaded {} rows from {}", data.size(), sourceName);
    }

    private void saveCsvToFile(File file) {
        logger.info("Saving CSV to file: {}", file.getName());
        view.setStatus("Saving to " + file.getName() + "...");

        CsvTableModel tableModel = view.getTableModel();
        boolean success = csvRepository.saveCsvToFile(tableModel.getData(), file);

        if (success) {
            view.setStatus("Saved %d rows to %s".formatted(tableModel.getRowCount(), file.getName()));
        } else {
            view.setStatus("Failed to save to " + file.getName());
        }
    }

    private void startUpdates() {
        updateEngine.start();
        updateRunning = true;
        view.setStartPauseLabel("Pause");
        view.setStatus("Real-time updates started");
        logger.info("Updates started");
    }

    private void pauseUpdates() {
        updateEngine.pause();
        updateRunning = false;
        view.setStartPauseLabel("Start");
        view.setStatus("Real-time updates paused");
        logger.info("Updates paused");
    }

    private void shutdown() {
        logger.info("Application closing...");
        updateEngine.shutdown();
    }
}

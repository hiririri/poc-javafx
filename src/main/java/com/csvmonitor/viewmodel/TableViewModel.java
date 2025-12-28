package com.csvmonitor.viewmodel;

import com.csvmonitor.model.CsvRepository;
import com.csvmonitor.model.RowModel;
import com.csvmonitor.model.UpdateEngine;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the CSV Table Monitor.
 * 
 * Responsibilities:
 * - Manages the data source (converts RowModel to RowViewModel)
 * - Provides column configurations for the View
 * - Handles commands: loadCsv, start, pause, unlockRow
 * - Coordinates with CsvRepository and UpdateEngine
 * - Exposes only RowViewModel to the View (not RowModel)
 */
public class TableViewModel {

    private static final Logger logger = LoggerFactory.getLogger(TableViewModel.class);

    // Internal data (Model layer)
    private final ObservableList<RowModel> masterData = FXCollections.observableArrayList();

    // Exposed data (ViewModel layer) - View should only access this
    private final ObservableList<RowViewModel> viewData = FXCollections.observableArrayList();

    // Model dependencies
    private final CsvRepository csvRepository = new CsvRepository();
    private final UpdateEngine updateEngine = new UpdateEngine();

    // UI state properties
    private final BooleanProperty updateRunning = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");
    private final IntegerProperty totalRowCount = new SimpleIntegerProperty(0);
    private final IntegerProperty filteredRowCount = new SimpleIntegerProperty(0);

    // Column configurations
    private final List<ColumnConfig<?>> columnConfigs = new ArrayList<>();

    public TableViewModel() {
        // Initialize column configurations
        initColumnConfigs();

        // Connect update engine to data source
        updateEngine.setData(masterData);

        // Sync masterData changes to viewData
        masterData.addListener((ListChangeListener<RowModel>) change -> {
            while (change.next()) {
                if (change.wasReplaced()) {
                    // Handle replacement
                    for (int i = change.getFrom(); i < change.getTo(); i++) {
                        viewData.set(i, new RowViewModel(masterData.get(i)));
                    }
                } else if (change.wasAdded()) {
                    int index = change.getFrom();
                    for (RowModel model : change.getAddedSubList()) {
                        viewData.add(index++, new RowViewModel(model));
                    }
                } else if (change.wasRemoved()) {
                    viewData.remove(change.getFrom(), change.getFrom() + change.getRemovedSize());
                }
            }
            totalRowCount.set(viewData.size());
        });

        logger.info("TableViewModel initialized");
    }

    /**
     * Initialize column configurations.
     * This defines how the View should render each column.
     */
    private void initColumnConfigs() {
        columnConfigs.add(ColumnConfig.numberColumn(
                "id", "ID", 90, 70,
                ColumnConfig.ALIGN_CENTER,
                RowViewModel::idProperty
        ));

        columnConfigs.add(ColumnConfig.stringColumn(
                "symbol", "Symbol", 110, 90,
                ColumnConfig.ALIGN_LEFT,
                RowViewModel::symbolProperty
        ));

        columnConfigs.add(ColumnConfig.numberColumnWithStyle(
                "price", "Price", 120, 100,
                ColumnConfig.ALIGN_RIGHT,
                RowViewModel::priceProperty,
                RowViewModel::getPriceStyleClass
        ));

        columnConfigs.add(ColumnConfig.numberColumn(
                "qty", "Qty", 100, 80,
                ColumnConfig.ALIGN_RIGHT,
                RowViewModel::qtyProperty
        ));

        columnConfigs.add(ColumnConfig.stringColumnWithStyle(
                "status", "Status", 110, 90,
                ColumnConfig.ALIGN_CENTER,
                RowViewModel::statusProperty,
                RowViewModel::getStatusStyleClass
        ));

        columnConfigs.add(ColumnConfig.stringColumn(
                "lastUpdate", "Last Update", 180, 150,
                ColumnConfig.ALIGN_LEFT,
                RowViewModel::lastUpdateProperty
        ));

        logger.info("Initialized {} column configurations", columnConfigs.size());
    }

    // ==================== Column Configurations ====================

    /**
     * Get column configurations for the View to create columns.
     */
    public List<ColumnConfig<?>> getColumnConfigs() {
        return List.copyOf(columnConfigs);
    }

    // ==================== Data Access ====================

    /**
     * Get the view data list (for View binding).
     * View should only access RowViewModel, not RowModel.
     */
    public ObservableList<RowViewModel> getViewData() {
        return viewData;
    }

    // ==================== Commands ====================

    /**
     * Load the default sample.csv from resources.
     */
    public void loadDefaultCsv() {
        logger.info("Loading default CSV...");
        statusMessage.set("Loading default CSV...");

        ObservableList<RowModel> data = csvRepository.loadDefaultCsv();
        viewData.clear();
        masterData.setAll(data);

        statusMessage.set("Loaded %d rows from sample.csv".formatted(data.size()));
        logger.info("Loaded {} rows from default CSV", data.size());
    }

    /**
     * Load CSV from an external file.
     */
    public void loadCsvFromFile(File file) {
        if (file == null) {
            return;
        }

        logger.info("Loading CSV from file: {}", file.getName());
        statusMessage.set("Loading " + file.getName() + "...");

        // Pause updates during load
        boolean wasRunning = updateEngine.isRunning();
        if (wasRunning) {
            updateEngine.pause();
        }

        ObservableList<RowModel> data = csvRepository.loadCsvFromFile(file);
        viewData.clear();
        masterData.setAll(data);

        // Resume if was running
        if (wasRunning) {
            updateEngine.start();
        }

        statusMessage.set("Loaded %d rows from %s".formatted(data.size(), file.getName()));
        logger.info("Loaded {} rows from {}", data.size(), file.getName());
    }

    /**
     * Save current data to a CSV file.
     */
    public boolean saveCsvToFile(File file) {
        if (file == null) {
            return false;
        }

        logger.info("Saving CSV to file: {}", file.getName());
        statusMessage.set("Saving to " + file.getName() + "...");

        boolean success = csvRepository.saveCsvToFile(new ArrayList<>(masterData), file);

        if (success) {
            statusMessage.set("Saved %d rows to %s".formatted(masterData.size(), file.getName()));
        } else {
            statusMessage.set("Failed to save to " + file.getName());
        }

        return success;
    }

    /**
     * Start real-time updates.
     */
    public void startUpdates() {
        updateEngine.start();
        updateRunning.set(true);
        statusMessage.set("Real-time updates started");
        logger.info("Updates started");
    }

    /**
     * Pause real-time updates.
     */
    public void pauseUpdates() {
        updateEngine.pause();
        updateRunning.set(false);
        statusMessage.set("Real-time updates paused");
        logger.info("Updates paused");
    }

    /**
     * Toggle update state.
     */
    public void toggleUpdates() {
        if (updateRunning.get()) {
            pauseUpdates();
        } else {
            startUpdates();
        }
    }

    // ==================== Row Operations ====================

    /**
     * Unlock a specific row immediately.
     */
    public void unlockRow(RowViewModel rowViewModel) {
        if (rowViewModel != null) {
            rowViewModel.getModel().unlock();
            logger.debug("Row {} unlocked", rowViewModel.getId());
        }
    }

    /**
     * Unlock all rows.
     */
    public void unlockAllRows() {
        masterData.forEach(RowModel::unlock);
        logger.info("All rows unlocked");
        statusMessage.set("All rows unlocked");
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown the ViewModel (call on application exit).
     */
    public void shutdown() {
        updateEngine.shutdown();
        logger.info("TableViewModel shutdown complete");
    }

    // ==================== Properties ====================

    public IntegerProperty filteredRowCountProperty() {
        return filteredRowCount;
    }

    public int getFilteredRowCount() {
        return filteredRowCount.get();
    }

    public void setFilteredRowCount(int count) {
        filteredRowCount.set(count);
    }

    public BooleanProperty updateRunningProperty() {
        return updateRunning;
    }

    public boolean isUpdateRunning() {
        return updateRunning.get();
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public IntegerProperty totalRowCountProperty() {
        return totalRowCount;
    }

    public int getTotalRowCount() {
        return totalRowCount.get();
    }
}

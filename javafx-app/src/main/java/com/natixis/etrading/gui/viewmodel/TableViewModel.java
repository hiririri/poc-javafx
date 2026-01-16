package com.natixis.etrading.gui.viewmodel;

import com.natixis.etrading.gui.service.CsvRepository;
import com.natixis.etrading.gui.model.RowModel;
import com.natixis.etrading.gui.service.UpdateEngine;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


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
        // Skip during bulk loading as we handle viewData directly for performance
        masterData.addListener((ListChangeListener<RowModel>) change -> {
            if (bulkLoading) {
                return; // Skip - bulk load handles viewData directly
            }
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

    // Batch size for async data loading to avoid UI thread blocking
    private static final int BATCH_SIZE = 200;

    // Flag to skip listener during bulk load (listener will create duplicate ViewModels)
    private volatile boolean bulkLoading = false;

    /**
     * Load the default sample.csv from resources asynchronously.
     * Data is loaded in background thread, RowViewModels are created in background,
     * and inserted in batches to avoid UI freezing.
     */
    public void loadDefaultCsv() {
        logger.info("Loading default CSV asynchronously...");
        statusMessage.set("Loading default CSV...");

        CompletableFuture.runAsync(() -> {
            // Load data in background thread
            ObservableList<RowModel> data = csvRepository.loadDefaultCsv();
            int totalSize = data.size();
            List<RowModel> dataList = new ArrayList<>(data);

            // Create RowViewModels in background thread (expensive operation)
            List<RowViewModel> viewModels = new ArrayList<>(totalSize);
            for (RowModel model : dataList) {
                viewModels.add(new RowViewModel(model));
            }

            // Clear existing data on UI thread first
            Platform.runLater(() -> {
                bulkLoading = true;
                viewData.clear();
                masterData.clear();
            });

            // Small delay to let clear complete
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // Add data in batches
            for (int i = 0; i < totalSize; i += BATCH_SIZE) {
                final int start = i;
                final int end = Math.min(i + BATCH_SIZE, totalSize);
                final List<RowModel> modelBatch = dataList.subList(start, end);
                final List<RowViewModel> viewModelBatch = viewModels.subList(start, end);

                Platform.runLater(() -> {
                    masterData.addAll(modelBatch);
                    viewData.addAll(viewModelBatch);
                    statusMessage.set("Loading... %d / %d rows".formatted(end, totalSize));
                });

                // Small delay between batches to let UI thread breathe
                if (end < totalSize) {
                    try {
                        Thread.sleep(16); // ~1 frame at 60fps
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            Platform.runLater(() -> {
                bulkLoading = false;
                totalRowCount.set(viewData.size());
                statusMessage.set("Loaded %d rows from sample.csv".formatted(totalSize));
                logger.info("Loaded {} rows from default CSV", totalSize);
            });
        }).exceptionally(ex -> {
            logger.error("Failed to load default CSV", ex);
            Platform.runLater(() -> {
                bulkLoading = false;
                statusMessage.set("Failed to load CSV: " + ex.getMessage());
            });
            return null;
        });
    }

    /**
     * Load CSV from an external file asynchronously.
     * Data is loaded in background thread, RowViewModels are created in background,
     * and inserted in batches to avoid UI freezing.
     */
    public void loadCsvFromFile(File file) {
        if (file == null) {
            return;
        }

        logger.info("Loading CSV from file asynchronously: {}", file.getName());
        statusMessage.set("Loading " + file.getName() + "...");

        // Pause updates during load
        boolean wasRunning = updateEngine.isRunning();
        if (wasRunning) {
            updateEngine.pause();
        }

        final String fileName = file.getName();

        CompletableFuture.runAsync(() -> {
            // Load data in background thread
            ObservableList<RowModel> data = csvRepository.loadCsvFromFile(file);
            int totalSize = data.size();
            List<RowModel> dataList = new ArrayList<>(data);

            // Create RowViewModels in background thread (expensive operation)
            List<RowViewModel> viewModels = new ArrayList<>(totalSize);
            for (RowModel model : dataList) {
                viewModels.add(new RowViewModel(model));
            }

            // Clear existing data on UI thread first
            Platform.runLater(() -> {
                bulkLoading = true;
                viewData.clear();
                masterData.clear();
            });

            // Small delay to let clear complete
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // Add data in batches
            for (int i = 0; i < totalSize; i += BATCH_SIZE) {
                final int start = i;
                final int end = Math.min(i + BATCH_SIZE, totalSize);
                final List<RowModel> modelBatch = dataList.subList(start, end);
                final List<RowViewModel> viewModelBatch = viewModels.subList(start, end);

                Platform.runLater(() -> {
                    masterData.addAll(modelBatch);
                    viewData.addAll(viewModelBatch);
                    statusMessage.set("Loading... %d / %d rows".formatted(end, totalSize));
                });

                // Small delay between batches to let UI thread breathe
                if (end < totalSize) {
                    try {
                        Thread.sleep(16); // ~1 frame at 60fps
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            Platform.runLater(() -> {
                bulkLoading = false;
                totalRowCount.set(viewData.size());
                // Resume if was running
                if (wasRunning) {
                    updateEngine.start();
                }
                statusMessage.set("Loaded %d rows from %s".formatted(totalSize, fileName));
                logger.info("Loaded {} rows from {}", totalSize, fileName);
            });
        }).exceptionally(ex -> {
            logger.error("Failed to load CSV from file: {}", fileName, ex);
            Platform.runLater(() -> {
                bulkLoading = false;
                if (wasRunning) {
                    updateEngine.start();
                }
                statusMessage.set("Failed to load " + fileName + ": " + ex.getMessage());
            });
            return null;
        });
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

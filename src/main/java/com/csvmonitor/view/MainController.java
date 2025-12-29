package com.csvmonitor.view;

import com.csvmonitor.viewmodel.ColumnConfig;
import com.csvmonitor.viewmodel.RowViewModel;
import com.csvmonitor.viewmodel.TableViewModel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.controlsfx.control.tableview2.FilteredTableColumn;
import org.controlsfx.control.tableview2.FilteredTableView;
import org.controlsfx.control.tableview2.filter.filtereditor.SouthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Controller for the main view.
 * 
 * MVVM Pattern:
 * - This is the View layer, binding UI components to the ViewModel
 * - No business logic - only UI binding and event forwarding
 * - No dependency on Model layer (only depends on ViewModel)
 * - Uses ControlsFX FilteredTableView and FilteredTableColumn for filtering
 */
public class MainController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // ViewModel reference (View only depends on ViewModel, not Model)
    private final TableViewModel viewModel = new TableViewModel();

    // FXML injected components
    @FXML
    private VBox tableContainer;
    @FXML
    private StackPane dockHost;

    @FXML
    private Button openCsvButton;
    @FXML
    private Button saveCsvButton;
    @FXML
    private Button startPauseButton;
    @FXML
    private Button unlockAllButton;

    @FXML
    private Label statusLabel;
    @FXML
    private Label rowCountLabel;

    // ControlsFX FilteredTableView - created programmatically
    private FilteredTableView<RowViewModel> filteredTableView;
    private DockingArea dockingArea;

    // Dynamic columns created from ViewModel configuration
    private final List<FilteredTableColumn<RowViewModel, ?>> columns = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing MainController...");

        createFilteredTableView();
        setupTableColumns();
        setupTableBinding();
        setupColumnFilters();
        setupToolbarBindings();

        restoreDockLayout();

        // Load default data on startup
        Platform.runLater(viewModel::loadDefaultCsv);

        logger.info("MainController initialized");
    }

    /**
     * Create the FilteredTableView programmatically.
     */
    private void createFilteredTableView() {
        filteredTableView = new FilteredTableView<>();
        filteredTableView.getStyleClass().add("data-table");
        filteredTableView.setEditable(false);
        filteredTableView.setTableMenuButtonVisible(true);

        // Set placeholder
        Label placeholder = new Label("No data loaded. Click 'Open CSV' to load data.");
        placeholder.getStyleClass().add("placeholder-text");
        filteredTableView.setPlaceholder(placeholder);

        dockingArea = new DockingArea();
        Tab tableTab = new Tab("Table", filteredTableView);
        dockingArea.addTab("table", tableTab);
        dockingArea.splitActiveProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                dockHost.getStyleClass().add("dock-host-active");
            } else {
                dockHost.getStyleClass().remove("dock-host-active");
            }
        });
        if (dockingArea.isSplitActive()) {
            dockHost.getStyleClass().add("dock-host-active");
        }

        // Add to container with VBox grow
        VBox.setVgrow(dockingArea, javafx.scene.layout.Priority.ALWAYS);
        tableContainer.getChildren().add(dockingArea);

        logger.info("FilteredTableView created");
    }

    /**
     * Create columns dynamically from ViewModel's column configurations.
     * No hardcoded column definitions - all from ViewModel.
     */
    private void setupTableColumns() {
        List<ColumnConfig<?>> configs = viewModel.getColumnConfigs();

        for (ColumnConfig<?> config : configs) {
            FilteredTableColumn<RowViewModel, ?> column = createColumn(config);
            columns.add(column);
            filteredTableView.getColumns().add(column);

            // Apply custom cell factories for styled columns
            if (config.hasCustomStyle()) {
                applyCellFactory(column, config);
            }
        }

        // Fix first two columns (id and symbol)
        if (columns.size() >= 2) {
            filteredTableView.getFixedColumns().add(columns.get(0));
            filteredTableView.getFixedColumns().add(columns.get(1));
        }

        logger.info("Created {} columns from ViewModel configuration", columns.size());
    }

    /**
     * Apply cell factory for styled columns.
     */
    @SuppressWarnings("unchecked")
    private void applyCellFactory(FilteredTableColumn<RowViewModel, ?> column, ColumnConfig<?> config) {
        if ("price".equals(config.id())) {
            // Price column needs special cell factory for flash animation
            FilteredTableColumn<RowViewModel, Number> priceCol = 
                    (FilteredTableColumn<RowViewModel, Number>) column;
            priceCol.setCellFactory(col -> new PriceTableCell());
        } else if ("status".equals(config.id())) {
            // Status column with styling
            FilteredTableColumn<RowViewModel, String> statusCol = 
                    (FilteredTableColumn<RowViewModel, String>) column;
            statusCol.setCellFactory(col -> new StatusTableCell());
        }
    }

    /**
     * Create a single column from configuration.
     */
    private <T> FilteredTableColumn<RowViewModel, T> createColumn(ColumnConfig<T> config) {
        FilteredTableColumn<RowViewModel, T> column = new FilteredTableColumn<>(config.title());

        // Set cell value factory from config
        column.setCellValueFactory(cellData -> {
            RowViewModel row = cellData.getValue();
            return (ObservableValue<T>) config.propertyGetter().apply(row);
        });

        // Set width and style
        column.setPrefWidth(config.prefWidth());
        column.setMinWidth(config.minWidth());
        column.setStyle(config.alignment());

        return column;
    }

    /**
     * Bind table to ViewModel's data.
     */
    private void setupTableBinding() {
        // Bind items to ViewModel's view data
        filteredTableView.setItems(viewModel.getViewData());

        // Row factory for conditional row styling (from ViewModel)
        filteredTableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(RowViewModel item, boolean empty) {
                super.updateItem(item, empty);

                // Clear all style classes first
                getStyleClass().removeAll("alert-row", "locked-row");

                if (empty || item == null) {
                    return;
                }

                // Apply row style from ViewModel
                String rowStyle = item.getRowStyleClass();
                if (rowStyle != null && !rowStyle.isEmpty()) {
                    getStyleClass().add(rowStyle);
                }

                if (item.isLocked()) {
                    getStyleClass().add("locked-row");
                }

                // Listen for style changes
                item.rowStyleClassProperty().addListener((obs, oldVal, newVal) -> {
                    Platform.runLater(() -> {
                        getStyleClass().removeAll("alert-row");
                        if (newVal != null && !newVal.isEmpty()) {
                            getStyleClass().add(newVal);
                        }
                    });
                });
            }
        });

        logger.info("Table binding configured");
    }

    /**
     * Setup column filters dynamically.
     */
    @SuppressWarnings("unchecked")
    private void setupColumnFilters() {
        List<ColumnConfig<?>> configs = viewModel.getColumnConfigs();

        for (int i = 0; i < columns.size(); i++) {
            ColumnConfig<?> config = configs.get(i);
            FilteredTableColumn<RowViewModel, ?> column = columns.get(i);

            // Create filter based on column type
            if (config.isNumberColumn()) {
                SouthFilter<RowViewModel, Number> filter = new SouthFilter<>(
                        (FilteredTableColumn<RowViewModel, Number>) column, Number.class);
                column.setSouthNode(filter);
            } else {
                SouthFilter<RowViewModel, String> filter = new SouthFilter<>(
                        (FilteredTableColumn<RowViewModel, String>) column, String.class);
                column.setSouthNode(filter);
            }
        }

        // Enable south header row for filters
        filteredTableView.setSouthHeaderBlended(true);

        logger.info("Column filters configured");
    }

    /**
     * Setup toolbar button bindings.
     */
    private void setupToolbarBindings() {
        // Start/Pause button text binding
        startPauseButton.textProperty().bind(
                Bindings.when(viewModel.updateRunningProperty())
                        .then("Pause")
                        .otherwise("Start"));

        // Status label binding
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());

        // Row count label binding
        rowCountLabel.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    int total = viewModel.getTotalRowCount();
                    int filtered = total;
                    if (filteredTableView.getPredicate() != null) {
                        filtered = (int) viewModel.getViewData().stream()
                                .filter(filteredTableView.getPredicate())
                                .count();
                    }
                    viewModel.setFilteredRowCount(filtered);
                    if (filtered == total) {
                        return "Rows: %d".formatted(total);
                    } else {
                        return "Rows: %d / %d".formatted(filtered, total);
                    }
                }, filteredTableView.predicateProperty(), viewModel.totalRowCountProperty()));
    }

    // ==================== Event Handlers ====================

    @FXML
    private void onOpenCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open CSV File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = chooser.showOpenDialog(filteredTableView.getScene().getWindow());
        if (file != null) {
            viewModel.loadCsvFromFile(file);
        }
    }

    @FXML
    private void onSaveCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save CSV File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName("export.csv");

        File file = chooser.showSaveDialog(filteredTableView.getScene().getWindow());
        if (file != null) {
            viewModel.saveCsvToFile(file);
        }
    }

    @FXML
    private void onStartPause() {
        viewModel.toggleUpdates();
    }

    @FXML
    private void onUnlockAll() {
        viewModel.unlockAllRows();
    }

    private void autoRepairLayoutIfNeeded(boolean restored) {
        if (!restored && dockingArea != null) {
            dockingArea.resetLayout();
            saveDockLayout();
            return;
        }
        if (dockingArea != null && dockingArea.saveLayout().isBlank()) {
            dockingArea.resetLayout();
            saveDockLayout();
        }
    }

    /**
     * Called when the application is closing.
     */
    public void shutdown() {
        saveDockLayout();
        viewModel.shutdown();
    }

    private void restoreDockLayout() {
        Preferences prefs = Preferences.userNodeForPackage(App.class);
        String layout = prefs.get("dock.layout", null);
        boolean restored = false;
        if (layout != null && !layout.isBlank()) {
            restored = dockingArea.restoreLayout(layout);
            if (!restored) {
                logger.warn("Failed to restore dock layout, using defaults");
            }
        }
        autoRepairLayoutIfNeeded(restored);
    }

    private void saveDockLayout() {
        Preferences prefs = Preferences.userNodeForPackage(App.class);
        String layout = dockingArea.saveLayout();
        if (layout == null || layout.isBlank()) {
            prefs.remove("dock.layout");
        } else {
            prefs.put("dock.layout", layout);
        }
        try {
            prefs.flush();
        } catch (Exception ex) {
            logger.warn("Failed to save dock layout: {}", ex.getMessage());
        }
    }

    // ==================== Custom Cell Classes ====================

    /**
     * Custom cell for the Price column with flash animation.
     * Gets style information from RowViewModel.
     */
    private class PriceTableCell extends TableCell<RowViewModel, Number> {

        private PauseTransition flashTimer;
        private double lastDisplayedValue = -1;

        public PriceTableCell() {
            setAlignment(Pos.CENTER_RIGHT);

            // Timer to remove flash effect after 500ms
            flashTimer = new PauseTransition(Duration.millis(500));
            flashTimer.setOnFinished(e -> {
                getStyleClass().removeAll("price-flash-up", "price-flash-down", "price-flash");
            });
        }

        @Override
        protected void updateItem(Number item, boolean empty) {
            super.updateItem(item, empty);

            // Clear style classes
            getStyleClass().removeAll("price-up", "price-down");

            if (empty || item == null) {
                setText(null);
                lastDisplayedValue = -1;
                return;
            }

            double currentValue = item.doubleValue();
            setText(String.format("%.2f", currentValue));

            // Get style from RowViewModel
            RowViewModel row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null) {
                String styleClass = row.getPriceStyleClass();
                if (styleClass != null && !styleClass.isEmpty()) {
                    getStyleClass().add(styleClass);
                }

                // Flash animation
                if (lastDisplayedValue >= 0 && lastDisplayedValue != currentValue) {
                    getStyleClass().removeAll("price-flash-up", "price-flash-down", "price-flash");

                    int direction = row.getPriceDirection();
                    if (direction > 0) {
                        getStyleClass().add("price-flash-up");
                    } else if (direction < 0) {
                        getStyleClass().add("price-flash-down");
                    } else {
                        getStyleClass().add("price-flash");
                    }

                    flashTimer.stop();
                    flashTimer.playFromStart();
                }
            }

            lastDisplayedValue = currentValue;
        }
    }

    /**
     * Custom cell for Status column with styling from RowViewModel.
     */
    private class StatusTableCell extends TableCell<RowViewModel, String> {

        private static final List<String> STATUS_CLASSES = List.of(
                "status-alert", "status-normal", "status-pending", "status-active", "status-closed");

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            // Clear status classes
            getStyleClass().removeAll(STATUS_CLASSES);

            if (empty || item == null) {
                setText(null);
                return;
            }

            setText(item);

            // Get style from RowViewModel
            RowViewModel row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null) {
                String styleClass = row.getStatusStyleClass();
                if (styleClass != null && !styleClass.isEmpty()) {
                    getStyleClass().add(styleClass);
                }
            }
        }
    }
}

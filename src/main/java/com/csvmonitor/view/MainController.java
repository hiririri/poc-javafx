package com.csvmonitor.view;

import com.csvmonitor.viewmodel.ColumnConfig;
import com.csvmonitor.viewmodel.RowViewModel;
import com.csvmonitor.viewmodel.TableViewModel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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
    @FXML
    private ToggleButton dockToggleButton;
    @FXML
    private BorderPane contentPane;
    @FXML
    private StackPane dockWorkspaceHolder;

    // ControlsFX FilteredTableView - created programmatically
    private FilteredTableView<RowViewModel> filteredTableView;

    // Dynamic columns created from ViewModel configuration
    private final List<FilteredTableColumn<RowViewModel, ?>> columns = new ArrayList<>();

    // Dock workspace manager
    private DockManager dockManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing MainController...");

        createFilteredTableView();
        setupTableColumns();
        setupTableBinding();
        setupColumnFilters();
        setupToolbarBindings();
        setupDockWorkspace();

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

        // Add to container with VBox grow
        VBox.setVgrow(filteredTableView, javafx.scene.layout.Priority.ALWAYS);
        tableContainer.getChildren().add(filteredTableView);

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

    @FXML
    private void onReloadSample() {
        viewModel.loadDefaultCsv();
    }

    @FXML
    private void onToggleDock(ActionEvent event) {
        if (event.getSource() != dockToggleButton) {
            dockToggleButton.setSelected(!dockToggleButton.isSelected());
        }
        updateDockVisibility();
    }

    /**
     * Called when the application is closing.
     */
    public void shutdown() {
        viewModel.shutdown();
    }

    // ==================== Dock Workspace ====================

    private void setupDockWorkspace() {
        dockManager = new DockManager(dockWorkspaceHolder);

        DockManager.DockItem statusItem = new DockManager.DockItem(
                "status",
                "状态",
                createStatusContent()
        );

        DockManager.DockItem actionsItem = new DockManager.DockItem(
                "actions",
                "操作",
                createActionsContent()
        );

        DockManager.DockItem filtersItem = new DockManager.DockItem(
                "filters",
                "过滤提示",
                createFilterHintContent()
        );

        dockManager.createArea("监控", statusItem, actionsItem, filtersItem);
        updateDockVisibility();
    }

    private Node createStatusContent() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        grid.add(new Label("状态"), 0, 0);
        Label statusValue = new Label();
        statusValue.textProperty().bind(viewModel.statusMessageProperty());
        grid.add(statusValue, 1, 0);

        grid.add(new Label("更新"), 0, 1);
        Label updateValue = new Label();
        updateValue.textProperty().bind(
                Bindings.when(viewModel.updateRunningProperty())
                        .then("运行中")
                        .otherwise("已暂停"));
        grid.add(updateValue, 1, 1);

        grid.add(new Label("总行数"), 0, 2);
        Label totalRows = new Label();
        totalRows.textProperty().bind(viewModel.totalRowCountProperty().asString());
        grid.add(totalRows, 1, 2);

        grid.add(new Label("已过滤"), 0, 3);
        Label filteredRows = new Label();
        filteredRows.textProperty().bind(viewModel.filteredRowCountProperty().asString());
        grid.add(filteredRows, 1, 3);

        grid.getStyleClass().add("dock-card");
        return grid;
    }

    private Node createActionsContent() {
        VBox box = new VBox(10);

        Button toggleButton = new Button();
        toggleButton.textProperty().bind(
                Bindings.when(viewModel.updateRunningProperty())
                        .then("暂停实时更新")
                        .otherwise("开始实时更新"));
        toggleButton.setOnAction(e -> viewModel.toggleUpdates());

        Button unlockButton = new Button("解锁全部");
        unlockButton.setOnAction(e -> viewModel.unlockAllRows());

        Button reloadButton = new Button("重新加载示例数据");
        reloadButton.setOnAction(e -> viewModel.loadDefaultCsv());

        box.getChildren().addAll(toggleButton, unlockButton, reloadButton);
        box.getStyleClass().add("dock-card");
        return box;
    }

    private Node createFilterHintContent() {
        VBox box = new VBox(6);
        box.getChildren().addAll(
                new Label("提示："),
                new Label("• 支持列筛选器（南向表头）组合过滤"),
                new Label("• 通过拖拽标签可将面板拆分到新分组"),
                new Label("• 拖到面板边缘可快速左右/上下分割")
        );
        box.getStyleClass().add("dock-card");
        return box;
    }

    private void updateDockVisibility() {
        boolean showDock = dockToggleButton.isSelected();
        dockWorkspaceHolder.setManaged(showDock);
        dockWorkspaceHolder.setVisible(showDock);
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

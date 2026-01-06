package com.csvmonitor.view;

import com.csvmonitor.viewmodel.ColumnConfig;
import com.csvmonitor.viewmodel.RowViewModel;
import com.csvmonitor.viewmodel.TableViewModel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.drombler.commons.docking.DockableKind;
import org.drombler.commons.docking.DockablePreferences;
import org.drombler.commons.docking.DockingAreaDescriptor;
import org.drombler.commons.docking.DockingAreaKind;
import org.drombler.commons.docking.LayoutConstraintsDescriptor;
import org.drombler.commons.docking.fx.DockingPane;
import org.drombler.commons.docking.fx.FXDockableData;
import org.drombler.commons.docking.fx.FXDockableEntry;
import org.controlsfx.control.tableview2.FilteredTableColumn;
import org.controlsfx.control.tableview2.FilteredTableView;
import org.controlsfx.control.tableview2.filter.filtereditor.SouthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Controller for the main view (View layer in MVVM pattern).
 * <p>
 * Responsibilities:
 * - Creates UI components programmatically (no FXML)
 * - Binds UI to ViewModel properties
 * - Forwards user events to ViewModel
 * - Applies cell styling based on status
 */
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // ==================== Constants ====================

    /** Background colors for each status type */
    private static final Map<String, Color> STATUS_BG_COLORS = new HashMap<>();
    static {
        // ALERT - red
        STATUS_BG_COLORS.put("ALERT", Color.web("#ffcccc"));
        STATUS_BG_COLORS.put("WARN", Color.web("#ffcccc"));
        STATUS_BG_COLORS.put("WARNING", Color.web("#ffcccc"));
        // NORMAL - green
        STATUS_BG_COLORS.put("NORMAL", Color.web("#e6ffe6"));
        STATUS_BG_COLORS.put("OK", Color.web("#e6ffe6"));
        STATUS_BG_COLORS.put("GOOD", Color.web("#e6ffe6"));
        // PENDING - orange/yellow
        STATUS_BG_COLORS.put("PENDING", Color.web("#fff2cc"));
        STATUS_BG_COLORS.put("WAIT", Color.web("#fff2cc"));
        // ACTIVE - blue
        STATUS_BG_COLORS.put("ACTIVE", Color.web("#cce6ff"));
        STATUS_BG_COLORS.put("LIVE", Color.web("#cce6ff"));
        STATUS_BG_COLORS.put("RUNNING", Color.web("#cce6ff"));
        // CLOSED - gray
        STATUS_BG_COLORS.put("CLOSED", Color.web("#e6e6e6"));
        STATUS_BG_COLORS.put("DONE", Color.web("#e6e6e6"));
        STATUS_BG_COLORS.put("COMPLETE", Color.web("#e6e6e6"));
    }

    // ==================== Fields ====================

    private final TableViewModel viewModel = new TableViewModel();
    private final List<FilteredTableColumn<RowViewModel, ?>> columns = new ArrayList<>();

    private DockingPane dockingPane;
    private VBox tableContainer;
    private FilteredTableView<RowViewModel> filteredTableView;
    private Button startPauseButton;
    private Label statusLabel;
    private Label rowCountLabel;
    private Label detailIdValue;
    private Label detailSymbolValue;
    private Label detailPriceValue;
    private Label detailQtyValue;
    private Label detailStatusValue;
    private Label detailUpdateValue;

    // ==================== Public Methods ====================

    /**
     * Creates and initializes the view.
     * @return the root node of the view
     */
    public DockingPane createView() {
        logger.info("Creating MainController view...");

        dockingPane = createDockLayout();

        createFilteredTableView();
        setupTableColumns();
        setupTableBinding();
        setupColumnFilters();
        setupToolbarBindings();
        setupDetailsBinding();

        Platform.runLater(viewModel::loadDefaultCsv);

        logger.info("MainController view created");
        return dockingPane;
    }

    /**
     * Called when the application is closing.
     */
    public void shutdown() {
        viewModel.shutdown();
    }

    // ==================== UI Creation ====================

    private VBox createToolbar() {
        // Title bar
        Label appTitle = new Label("CSV Table Monitor");
        appTitle.getStyleClass().add("app-title");

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        rowCountLabel = new Label("Rows: 0");
        rowCountLabel.getStyleClass().add("info-label");

        HBox titleBar = new HBox(12, appTitle, titleSpacer, rowCountLabel);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        // File buttons
        Button openCsvButton = new Button("Open CSV");
        openCsvButton.getStyleClass().add("toolbar-button");
        openCsvButton.setOnAction(e -> onOpenCsv());

        Button saveCsvButton = new Button("Save CSV");
        saveCsvButton.getStyleClass().add("toolbar-button");
        saveCsvButton.setOnAction(e -> onSaveCsv());

        HBox fileGroup = new HBox(6, openCsvButton, saveCsvButton);
        fileGroup.getStyleClass().add("button-group");

        // Control buttons
        startPauseButton = new Button("Start");
        startPauseButton.getStyleClass().addAll("toolbar-button", "primary-button");
        startPauseButton.setPrefWidth(100);
        startPauseButton.setOnAction(e -> onStartPause());

        Button unlockAllButton = new Button("Unlock All");
        unlockAllButton.getStyleClass().add("toolbar-button");
        unlockAllButton.setOnAction(e -> onUnlockAll());

        HBox controlGroup = new HBox(6, startPauseButton, unlockAllButton);
        controlGroup.getStyleClass().add("button-group");

        // Toolbar layout
        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);

        HBox mainToolbar = new HBox(12,
                fileGroup,
                new Separator(Orientation.VERTICAL),
                controlGroup,
                new Separator(Orientation.VERTICAL),
                toolbarSpacer);
        mainToolbar.setAlignment(Pos.CENTER_LEFT);
        mainToolbar.getStyleClass().add("main-toolbar");

        VBox toolbarContainer = new VBox(8, titleBar, mainToolbar);
        toolbarContainer.getStyleClass().add("toolbar-container");
        toolbarContainer.setPadding(new Insets(12, 16, 8, 16));

        return toolbarContainer;
    }

    private VBox createTableSection() {
        tableContainer = new VBox();
        tableContainer.getStyleClass().add("table-container");
        tableContainer.setPadding(new Insets(8));
        return tableContainer;
    }

    private DockingPane createDockLayout() {
        DockingPane pane = new DockingPane();
        pane.getStyleClass().add("root-pane");

        VBox toolbar = createToolbar();
        VBox tableSection = createTableSection();
        HBox statusBar = createStatusBar();
        VBox detailsPanel = createDetailsPanel();

        DockingAreaDescriptor toolbarArea = createDockingArea(
                "toolbar-area", DockingAreaKind.VIEW, 0, List.of(),
                LayoutConstraintsDescriptor.prefHeight(110), true);
        DockingAreaDescriptor contentArea = createDockingArea(
                "content-area", DockingAreaKind.VIEW, 0, List.of(1),
                LayoutConstraintsDescriptor.flexible(), true);
        DockingAreaDescriptor detailsArea = createDockingArea(
                "details-area", DockingAreaKind.VIEW, 1, List.of(1),
                LayoutConstraintsDescriptor.prefWidth(280), true);
        DockingAreaDescriptor statusArea = createDockingArea(
                "status-area", DockingAreaKind.VIEW, 2, List.of(),
                LayoutConstraintsDescriptor.prefHeight(44), true);

        pane.getDockingAreaDescriptors().addAll(Set.of(toolbarArea, contentArea, detailsArea, statusArea));

        pane.getDockables().addAll(Set.of(createDockable(toolbar, "Toolbar", "toolbar-area"),
                                          createDockable(tableSection, "Table", "content-area"),
                                          createDockable(detailsPanel, "Details", "details-area"),
                                          createDockable(statusBar, "Status", "status-area")));

        return pane;
    }

    private DockingAreaDescriptor createDockingArea(String id, DockingAreaKind kind, int position,
                                                    List<Integer> parentPath,
                                                    LayoutConstraintsDescriptor layoutConstraints,
                                                    boolean permanent) {
        DockingAreaDescriptor area = new DockingAreaDescriptor();
        area.setId(id);
        area.setKind(kind);
        area.setPosition(position);
        area.setParentPath(parentPath);
        area.setLayoutConstraints(layoutConstraints);
        area.setPermanent(permanent);
        return area;
    }

    private FXDockableEntry createDockable(Region content, String title, String areaId) {
        FXDockableData data = new FXDockableData();
        data.setTitle(title);
        DockablePreferences preferences = new DockablePreferences(areaId, 0);
        return new FXDockableEntry(content, DockableKind.VIEW, data, preferences);
    }

    private VBox createDetailsPanel() {
        Label header = new Label("Selection Details");
        header.getStyleClass().add("details-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);

        detailIdValue = new Label("-");
        detailSymbolValue = new Label("-");
        detailPriceValue = new Label("-");
        detailQtyValue = new Label("-");
        detailStatusValue = new Label("-");
        detailUpdateValue = new Label("-");

        addDetailRow(grid, 0, "ID", detailIdValue);
        addDetailRow(grid, 1, "Symbol", detailSymbolValue);
        addDetailRow(grid, 2, "Price", detailPriceValue);
        addDetailRow(grid, 3, "Qty", detailQtyValue);
        addDetailRow(grid, 4, "Status", detailStatusValue);
        addDetailRow(grid, 5, "Last Update", detailUpdateValue);

        VBox panel = new VBox(10, header, grid);
        panel.getStyleClass().add("details-panel");
        panel.setPadding(new Insets(10));

        return panel;
    }

    private void addDetailRow(GridPane grid, int row, String label, Label value) {
        Label key = new Label(label + ":");
        key.getStyleClass().add("details-label");
        value.getStyleClass().add("details-value");
        grid.addRow(row, key, value);
    }

    private HBox createStatusBar() {
        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hintLabel = new Label("Use column filters to search data");
        hintLabel.getStyleClass().add("hint-text");

        HBox statusBar = new HBox(12, statusLabel, spacer, hintLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(8, 16, 8, 16));

        return statusBar;
    }

    private void createFilteredTableView() {
        filteredTableView = new FilteredTableView<>();
        filteredTableView.getStyleClass().add("data-table");
        filteredTableView.setEditable(false);
        filteredTableView.setTableMenuButtonVisible(true);

        Label placeholder = new Label("No data loaded. Click 'Open CSV' to load data.");
        placeholder.getStyleClass().add("placeholder-text");
        filteredTableView.setPlaceholder(placeholder);

        VBox.setVgrow(filteredTableView, Priority.ALWAYS);
        tableContainer.getChildren().add(filteredTableView);

        logger.info("FilteredTableView created");
    }

    // ==================== Table Setup ====================

    @SuppressWarnings("unchecked")
    private void setupTableColumns() {
        List<ColumnConfig<?>> configs = viewModel.getColumnConfigs();

        for (ColumnConfig<?> config : configs) {
            FilteredTableColumn<RowViewModel, ?> column = createColumn(config);
            columns.add(column);
            filteredTableView.getColumns().add(column);

            // Apply appropriate cell factory
            if ("price".equals(config.id())) {
                ((FilteredTableColumn<RowViewModel, Number>) column)
                        .setCellFactory(col -> new PriceTableCell());
            } else if ("status".equals(config.id())) {
                ((FilteredTableColumn<RowViewModel, String>) column)
                        .setCellFactory(col -> new StatusTableCell());
            } else if (config.isNumberColumn()) {
                ((FilteredTableColumn<RowViewModel, Number>) column)
                        .setCellFactory(col -> new ColoredCell<>(Pos.CENTER_RIGHT));
            } else {
                ((FilteredTableColumn<RowViewModel, String>) column)
                        .setCellFactory(col -> new ColoredCell<>(Pos.CENTER_LEFT));
            }
        }

        // Fix first two columns (id and symbol)
        if (columns.size() >= 2) {
            filteredTableView.getFixedColumns().add(columns.get(0));
            filteredTableView.getFixedColumns().add(columns.get(1));
        }

        logger.info("Created {} columns from ViewModel configuration", columns.size());
    }

    private <T> FilteredTableColumn<RowViewModel, T> createColumn(ColumnConfig<T> config) {
        FilteredTableColumn<RowViewModel, T> column = new FilteredTableColumn<>(config.title());

        column.setCellValueFactory(cellData -> 
                (ObservableValue<T>) config.propertyGetter().apply(cellData.getValue()));

        column.setPrefWidth(config.prefWidth());
        column.setMinWidth(config.minWidth());
        column.setStyle(config.alignment());

        // Click filter icon to clear this column's filter
        column.setOnFilterAction(e -> column.setPredicate(null));

        return column;
    }

    private void setupTableBinding() {
        filteredTableView.setItems(viewModel.getViewData());
        logger.info("Table binding configured");
    }

    @SuppressWarnings("unchecked")
    private void setupColumnFilters() {
        List<ColumnConfig<?>> configs = viewModel.getColumnConfigs();

        for (int i = 0; i < columns.size(); i++) {
            ColumnConfig<?> config = configs.get(i);
            FilteredTableColumn<RowViewModel, ?> column = columns.get(i);

            if (config.isNumberColumn()) {
                column.setSouthNode(new SouthFilter<>(
                        (FilteredTableColumn<RowViewModel, Number>) column, Number.class));
            } else {
                column.setSouthNode(new SouthFilter<>(
                        (FilteredTableColumn<RowViewModel, String>) column, String.class));
            }
        }

        filteredTableView.setSouthHeaderBlended(true);
        logger.info("Column filters configured");
    }

    private void setupToolbarBindings() {
        startPauseButton.textProperty().bind(
                Bindings.when(viewModel.updateRunningProperty())
                        .then("Pause")
                        .otherwise("Start"));

        statusLabel.textProperty().bind(viewModel.statusMessageProperty());

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
                    return filtered == total
                            ? "Rows: %d".formatted(total)
                            : "Rows: %d / %d".formatted(filtered, total);
                }, filteredTableView.predicateProperty(), viewModel.totalRowCountProperty()));
    }

    private void setupDetailsBinding() {
        filteredTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> updateDetailsPanel(newSelection));
        updateDetailsPanel(null);
    }

    private void updateDetailsPanel(RowViewModel row) {
        if (row == null) {
            detailIdValue.setText("-");
            detailSymbolValue.setText("-");
            detailPriceValue.setText("-");
            detailQtyValue.setText("-");
            detailStatusValue.setText("-");
            detailUpdateValue.setText("-");
            return;
        }

        detailIdValue.setText(String.valueOf(row.getId()));
        detailSymbolValue.setText(row.getSymbol());
        detailPriceValue.setText(String.format("%.5f", row.getPrice()));
        detailQtyValue.setText(String.valueOf(row.getQty()));
        detailStatusValue.setText(row.getStatus());
        detailUpdateValue.setText(row.getLastUpdate());
    }

    // ==================== Event Handlers ====================

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

    private void onStartPause() {
        viewModel.toggleUpdates();
    }

    private void onUnlockAll() {
        viewModel.unlockAllRows();
    }

    // ==================== Cell Background Helper ====================

    private Color getStatusBackgroundColor(String status) {
        return status == null ? null : STATUS_BG_COLORS.get(status.toUpperCase());
    }

    private void applyCellBackground(TableCell<RowViewModel, ?> cell) {
        RowViewModel row = cell.getTableRow() != null ? cell.getTableRow().getItem() : null;
        if (row != null) {
            Color color = getStatusBackgroundColor(row.getStatus());
            if (color != null) {
                cell.setBackground(new Background(new BackgroundFill(color, CornerRadii.EMPTY, Insets.EMPTY)));
                return;
            }
        }
        cell.setBackground(Background.EMPTY);
    }

    // ==================== Custom Cell Classes ====================

    /**
     * Generic cell with background color based on row status.
     */
    private class ColoredCell<T> extends TableCell<RowViewModel, T> {
        
        ColoredCell(Pos alignment) {
            setAlignment(alignment);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setBackground(Background.EMPTY);
            } else {
                setText(item.toString());
                applyCellBackground(this);
            }
        }
    }

    /**
     * Price cell with flash animation and special decimal formatting.
     */
    private class PriceTableCell extends TableCell<RowViewModel, Number> {

        private static final double NORMAL_FONT_SIZE = 12.0;
        private static final double HIGHLIGHT_FONT_SIZE = 14.0;

        private final TextFlow textFlow;
        private final Text prefixText;
        private final Text highlightText;
        private final Text suffixText;
        private final PauseTransition flashTimer;

        private double lastValue = -1;
        private int lastIndex = -1;

        PriceTableCell() {
            setAlignment(Pos.CENTER_RIGHT);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

            prefixText = new Text();
            prefixText.setFont(Font.font("System", FontWeight.NORMAL, NORMAL_FONT_SIZE));

            highlightText = new Text();
            highlightText.setFont(Font.font("System", FontWeight.BOLD, HIGHLIGHT_FONT_SIZE));

            suffixText = new Text();
            suffixText.setFont(Font.font("System", FontWeight.NORMAL, NORMAL_FONT_SIZE));

            textFlow = new TextFlow(prefixText, highlightText, suffixText);

            flashTimer = new PauseTransition(Duration.millis(500));
            flashTimer.setOnFinished(e -> 
                    getStyleClass().removeAll("price-flash-up", "price-flash-down", "price-flash"));
        }

        @Override
        protected void updateItem(Number item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("price-up", "price-down");

            if (empty || item == null) {
                clearCell();
                return;
            }

            double value = item.doubleValue();
            int index = getIndex();

            formatPrice(value);
            setGraphic(textFlow);
            applyCellBackground(this);

            handleFlashAnimation(value, index);
            applyPriceStyle();

            lastIndex = index;
            lastValue = value;
        }

        private void clearCell() {
            setGraphic(null);
            prefixText.setText("");
            highlightText.setText("");
            suffixText.setText("");
            lastValue = -1;
            lastIndex = -1;
            flashTimer.stop();
            getStyleClass().removeAll("price-flash-up", "price-flash-down", "price-flash");
            setBackground(Background.EMPTY);
        }

        private void formatPrice(double value) {
            String formatted = String.format("%.5f", value);
            int dot = formatted.indexOf('.');
            prefixText.setText(formatted.substring(0, dot + 3));
            highlightText.setText(formatted.substring(dot + 3, dot + 5));
            suffixText.setText(formatted.substring(dot + 5));
        }

        private void handleFlashAnimation(double value, int index) {
            boolean recycled = lastIndex != -1 && lastIndex != index;

            if (recycled) {
                flashTimer.stop();
                getStyleClass().removeAll("price-flash-up", "price-flash-down", "price-flash");
                lastValue = -1;
                return;
            }

            if (lastValue >= 0 && lastValue != value) {
                getStyleClass().removeAll("price-flash-up", "price-flash-down", "price-flash");

                RowViewModel row = getTableRow() != null ? getTableRow().getItem() : null;
                int direction = row != null ? row.getPriceDirection() 
                        : (value > lastValue ? 1 : -1);

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

        private void applyPriceStyle() {
            RowViewModel row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null) {
                String styleClass = row.getPriceStyleClass();
                if (styleClass != null && !styleClass.isEmpty()) {
                    getStyleClass().add(styleClass);
                }
            }
        }
    }

    /**
     * Status cell with text styling.
     */
    private class StatusTableCell extends TableCell<RowViewModel, String> {

        private static final List<String> STATUS_CLASSES = List.of(
                "status-alert", "status-normal", "status-pending", "status-active", "status-closed");

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll(STATUS_CLASSES);

            if (empty || item == null) {
                setText(null);
                setBackground(Background.EMPTY);
                return;
            }

            setText(item);
            applyCellBackground(this);

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

package com.csvmonitor.view;

import com.csvmonitor.viewmodel.ColumnConfig;
import com.csvmonitor.viewmodel.RowViewModel;
import com.csvmonitor.viewmodel.TableViewModel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.scene.CacheHint;
import org.controlsfx.control.tableview2.FilteredTableColumn;
import org.controlsfx.control.tableview2.FilteredTableView;
import org.controlsfx.control.tableview2.filter.filtereditor.SouthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

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

    private static final List<String> ROW_STATUS_CLASSES = List.of(
            "row-alert", "row-normal", "row-pending", "row-active", "row-closed");
    private static final String SELECTED_ROW_CLASS = "row-selected";

    // ==================== Fields ====================

    private final TableViewModel viewModel = new TableViewModel();
    private final List<FilteredTableColumn<RowViewModel, ?>> columns = new ArrayList<>();

    private BorderPane rootPane;
    private VBox tableContainer;
    private FilteredTableView<RowViewModel> filteredTableView;
    private Button startPauseButton;
    private Label statusLabel;
    private Label rowCountLabel;

    // ==================== Public Methods ====================

    /**
     * Creates and initializes the view.
     * @return the root node of the view
     */
    public BorderPane createView() {
        logger.info("Creating MainController view...");

        rootPane = new BorderPane();
        rootPane.getStyleClass().add("root-pane");
        rootPane.setTop(createToolbar());
        rootPane.setCenter(createTableSection());
        rootPane.setBottom(createStatusBar());

        createFilteredTableView();
        setupTableColumns();
        setupTableBinding();
        setupColumnFilters();
        setupToolbarBindings();

        Platform.runLater(viewModel::loadDefaultCsv);

        logger.info("MainController view created");
        return rootPane;
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
        filteredTableView.setFixedCellSize(24);
        filteredTableView.setRowFactory(tableView -> {
            TableRow<RowViewModel> row = new TableRow<>() {
                private final ChangeListener<String> rowStyleListener =
                        (obs, oldValue, newValue) -> applyRowStyle(this);
                private RowViewModel lastItem;

                @Override
                protected void updateItem(RowViewModel item, boolean empty) {
                    if (lastItem != null) {
                        lastItem.rowStyleClassProperty().removeListener(rowStyleListener);
                    }
                    super.updateItem(item, empty);
                    lastItem = empty ? null : item;
                    if (lastItem != null) {
                        lastItem.rowStyleClassProperty().addListener(rowStyleListener);
                    }
                    applyRowStyle(this);
                    applyRowSelectionStyle(this);
                }
            };
            row.selectedProperty().addListener((obs, oldValue, newValue) -> applyRowSelectionStyle(row));
            return row;
        });

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
                // Number columns with transparent background
                ((FilteredTableColumn<RowViewModel, Number>) column)
                        .setCellFactory(col -> new TransparentNumberCell());
            } else {
                // String columns with transparent background
                ((FilteredTableColumn<RowViewModel, String>) column)
                        .setCellFactory(col -> new TransparentTextCell());
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

    // ==================== Row Styling Helper ====================

    // Background colors for each status
    private static final String ALERT_BG = "#ffcccc";
    private static final String NORMAL_BG = "#e6ffe6";
    private static final String PENDING_BG = "#fff2cc";
    private static final String ACTIVE_BG = "#cce6ff";
    private static final String CLOSED_BG = "#e6e6e6";

    private void applyRowStyle(TableRow<RowViewModel> row) {
        if (row == null) {
            return;
        }
        row.getStyleClass().removeAll(ROW_STATUS_CLASSES);
        RowViewModel rowViewModel = row.getItem();
        if (rowViewModel == null || row.isEmpty()) {
            return;
        }
        String styleClass = rowViewModel.getRowStyleClass();
        if (styleClass != null && !styleClass.isEmpty()) {
            row.getStyleClass().add(styleClass);
        }
    }

    private void applyRowSelectionStyle(TableRow<RowViewModel> row) {
        if (row == null) {
            return;
        }
        if (row.isSelected()) {
            if (!row.getStyleClass().contains(SELECTED_ROW_CLASS)) {
                row.getStyleClass().add(SELECTED_ROW_CLASS);
            }
        } else {
            row.getStyleClass().remove(SELECTED_ROW_CLASS);
        }
    }

    /**
     * Get background color for a row based on its status.
     */
    private String getRowBackground(int index) {
        if (index < 0 || index >= filteredTableView.getItems().size()) {
            return null;
        }
        RowViewModel row = filteredTableView.getItems().get(index);
        if (row == null) return null;
        String styleClass = row.getRowStyleClass();
        if (styleClass == null || styleClass.isEmpty()) return null;
        return switch (styleClass) {
            case "row-alert" -> ALERT_BG;
            case "row-normal" -> NORMAL_BG;
            case "row-pending" -> PENDING_BG;
            case "row-active" -> ACTIVE_BG;
            case "row-closed" -> CLOSED_BG;
            default -> null;
        };
    }

    /**
     * Build inline style for cell background.
     * Uses multiple properties to ensure it overrides default styles on all platforms.
     */
    private String buildCellStyle(String bgColor) {
        if (bgColor == null) return null;
        return "-fx-background-color: " + bgColor + "; " +
               "-fx-background-insets: 0; " +
               "-fx-background: " + bgColor + ";";
    }

    // ==================== Custom Cell Classes ====================

    /**
     * Text cell with row-based background color.
     */
    private class TransparentTextCell extends TableCell<RowViewModel, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(null);
            } else {
                setText(item);
                setStyle(buildCellStyle(getRowBackground(getIndex())));
            }
        }
    }

    /**
     * Number cell with row-based background color.
     */
    private class TransparentNumberCell extends TableCell<RowViewModel, Number> {
        @Override
        protected void updateItem(Number item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(null);
            } else {
                setText(item.toString());
                setStyle(buildCellStyle(getRowBackground(getIndex())));
            }
        }
    }

    /**
     * Price cell with flash animation and special decimal formatting.
     */
    private class PriceTableCell extends TableCell<RowViewModel, Number> {

        private static final double NORMAL_FONT_SIZE = 12.0;
        private static final double HIGHLIGHT_FONT_SIZE = 14.0;
        private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("0.00000");

        private final TextFlow textFlow;
        private final Text prefixText;
        private final Text highlightText;
        private final Text suffixText;
        private final PauseTransition flashTimer;

        private double lastValue = -1;
        private int lastIndex = -1;
        private RowViewModel lastRow;

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
            textFlow.setCache(true);
            textFlow.setCacheHint(CacheHint.SPEED);

            flashTimer = new PauseTransition(Duration.millis(500));
            flashTimer.setOnFinished(e -> 
                    getStyleClass().removeAll("price-flash"));
        }

        @Override
        protected void updateItem(Number item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                clearCell();
                return;
            }

            double value = item.doubleValue();
            int index = getIndex();
            RowViewModel row = getTableRow() != null ? getTableRow().getItem() : null;

            if (row != lastRow) {
                flashTimer.stop();
                getStyleClass().removeAll("price-flash");
                lastValue = -1;
            }

            formatPrice(value);
            setGraphic(textFlow);

            handleFlashAnimation(value, index);
            applyPriceStyle();
            
            // Apply row background
            setStyle(buildCellStyle(getRowBackground(index)));

            lastIndex = index;
            lastValue = value;
            lastRow = row;
        }

        private void clearCell() {
            setGraphic(null);
            setStyle(null);
            prefixText.setText("");
            highlightText.setText("");
            suffixText.setText("");
            lastValue = -1;
            lastIndex = -1;
            lastRow = null;
            flashTimer.stop();
            getStyleClass().removeAll("price-flash");
        }

        private void formatPrice(double value) {
            String formatted = PRICE_FORMAT.format(value);
            int dot = formatted.indexOf('.');
            prefixText.setText(formatted.substring(0, dot + 3));
            highlightText.setText(formatted.substring(dot + 3, dot + 5));
            suffixText.setText(formatted.substring(dot + 5));
        }

        private void handleFlashAnimation(double value, int index) {
            boolean recycled = lastIndex != -1 && lastIndex != index;

            if (recycled) {
                flashTimer.stop();
                getStyleClass().removeAll( "price-flash");
                lastValue = -1;
                return;
            }

            if (lastValue >= 0 && lastValue != value) {
                getStyleClass().removeAll("price-flash");

                getStyleClass().add("price-flash");
                

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
                setStyle(null);
                return;
            }

            setText(item);

            // Apply status text color
            RowViewModel row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null) {
                String styleClass = row.getStatusStyleClass();
                if (styleClass != null && !styleClass.isEmpty()) {
                    getStyleClass().add(styleClass);
                }
            }
            
            // Apply row background
            setStyle(buildCellStyle(getRowBackground(getIndex())));
        }
    }
}

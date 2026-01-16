package com.natixis.etrading.gui.view;

import com.natixis.etrading.gui.viewmodel.ColumnConfig;
import com.natixis.etrading.gui.viewmodel.RowViewModel;
import com.natixis.etrading.gui.viewmodel.TableViewModel;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.util.Duration;
import org.controlsfx.control.tableview2.FilteredTableColumn;
import org.controlsfx.control.tableview2.FilteredTableView;
import org.controlsfx.control.tableview2.filter.filtereditor.SouthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;


public class TableController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(TableController.class);

    // ==================== Constants ====================

    private static final List<String> ROW_STATUS_CLASSES = List.of("row-alert", "row-normal", "row-pending", "row-active", "row-closed");
    // Selection styling constants
    private static final String SELECTED_ROW_BG = "#3498db";
    private static final String SELECTED_CELL_BORDER = "-fx-border-color: #1f6fb2; -fx-border-width: 2px;";

    // ==================== Fields ====================

    private final TableViewModel viewModel = new TableViewModel();
    private final List<FilteredTableColumn<RowViewModel, ?>> columns = new ArrayList<>();
    private boolean selectionRefreshPending = false;

    // FXML injected fields
    @FXML
    private VBox tableContainer;
    @FXML
    private FilteredTableView<RowViewModel> filteredTableView;
    @FXML
    private Button startPauseButton;
    @FXML
    private Button openCsvButton;
    @FXML
    private Button saveCsvButton;
    @FXML
    private Button unlockAllButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label rowCountLabel;
    @FXML
    private Label appTitle;

    // ==================== Public Methods ====================

    /**
     * Creates and loads the FXML view.
     *
     * @return the root node of the view
     */
    public BorderPane createView() {
        try {
            logger.info("Loading FXML view...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/table-view.fxml"));
            BorderPane root = loader.load();
            root.getStyleClass().add("root-pane");
            logger.info("FXML view loaded successfully");
            return root;
        } catch (IOException e) {
            logger.error("Failed to load FXML view", e);
            throw new RuntimeException("Failed to load FXML view", e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing TableController...");

        setupFilteredTableView();
        setupTableColumns();
        setupTableBinding();
        setupColumnFilters();
        setupToolbarBindings();

        Platform.runLater(viewModel::loadDefaultCsv);

        logger.info("TableController initialized");
    }

    /**
     * Called when the application is closing.
     */
    public void shutdown() {
        viewModel.shutdown();
    }

    // ==================== Table Setup ====================

    private void setupFilteredTableView() {
        filteredTableView.setEditable(false);
        filteredTableView.setTableMenuButtonVisible(true);
        filteredTableView.setFixedCellSize(24);

        Label placeholder = new Label("No data loaded. Click 'Open CSV' to load data.");
        placeholder.getStyleClass().add("placeholder-text");
        filteredTableView.setPlaceholder(placeholder);

        filteredTableView.setRowFactory(tableView -> {
            TableRow<RowViewModel> row = new TableRow<>();
            // Add double-click handler to show row details popup
            row.setOnMouseClicked(event -> {
                logger.debug("Mouse clicked on row: button={}, clickCount={}, item={}",
                        event.getButton(), event.getClickCount(), row.getItem());

                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    RowViewModel rowData = row.getItem();
                    if (rowData != null && !row.isEmpty()) {
                        logger.info("Double-click detected on row with ID: {}", rowData.getId());
                        event.consume(); // Consume the event to prevent other handlers
                        Platform.runLater(() -> showRowDetailsPopup(rowData));
                    } else {
                        logger.debug("Double-click on empty row or null data");
                    }
                }
            });

            return row;
        });

        setupSelectionRefresh();

        // Alternative: Add double-click handler directly to the table
        filteredTableView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                RowViewModel selectedItem = filteredTableView.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    logger.info("Table double-click detected on row with ID: {}", selectedItem.getId());
                    event.consume();
                    Platform.runLater(() -> showRowDetailsPopup(selectedItem));
                }
            }
        });

        logger.info("FilteredTableView setup completed");
    }

    private void setupSelectionRefresh() {
        TableView.TableViewSelectionModel<RowViewModel> selectionModel = filteredTableView.getSelectionModel();
        if (selectionModel != null) {
            selectionModel.selectedIndexProperty().addListener((obs, oldVal, newVal) -> requestSelectionRefresh());
            selectionModel.getSelectedCells().addListener(new ListChangeListener<>() {
                @Override
                public void onChanged(Change<? extends TablePosition> change) {
                    requestSelectionRefresh();
                }
            });
        }

        TableView.TableViewFocusModel<RowViewModel> focusModel = filteredTableView.getFocusModel();
        if (focusModel != null) {
            focusModel.focusedCellProperty().addListener((obs, oldVal, newVal) -> requestSelectionRefresh());
        }
    }

    private void requestSelectionRefresh() {
        if (selectionRefreshPending) {
            return;
        }
        selectionRefreshPending = true;
        Platform.runLater(() -> {
            selectionRefreshPending = false;
            filteredTableView.refresh();
        });
    }

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

        column.setCellValueFactory(cellData -> (ObservableValue<T>) config.propertyGetter().apply(cellData.getValue()));

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

    // ==================== FXML Event Handlers ====================

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

    /**
     * Shows a popup dialog with row details when a row is double-clicked.
     */
    private void showRowDetailsPopup(RowViewModel rowData) {
        logger.info("Showing details for row ID: {}", rowData.getId());

        // Create the dialog
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Row Details");
        dialog.setHeaderText("Row Information");

        // Create detailed content
        StringBuilder content = new StringBuilder();
        content.append("Row ID: ").append(rowData.getId()).append("\n");
        content.append("Symbol: ").append(rowData.getSymbol()).append("\n");
        content.append("Price: ").append(String.format("%.5f", rowData.getPrice())).append("\n");
        content.append("Quantity: ").append(rowData.getQty()).append("\n");
        content.append("Status: ").append(rowData.getStatus()).append("\n");
        content.append("Last Update: ").append(rowData.getLastUpdate()).append("\n");

        dialog.setContentText(content.toString());

        // Set dialog properties
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(filteredTableView.getScene().getWindow());
        dialog.setResizable(true);

        // Show the dialog
        dialog.showAndWait();
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
            row.setStyle(null);
            return;
        }
        String styleClass = rowViewModel.getRowStyleClass();
        if (styleClass != null && !styleClass.isEmpty()) {
            row.getStyleClass().add(styleClass);
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
    private String buildCellStyle(String bgColor, boolean cellFocused) {
        if (bgColor == null && !cellFocused) {
            return null;
        }

        StringBuilder style = new StringBuilder();
        if (bgColor != null) {
            style.append("""
                    -fx-background-color: %s;
                    -fx-background-insets: 0;
                    -fx-background: %s;
                    """.formatted(bgColor, bgColor));
        }
        if (cellFocused) {
            style.append(SELECTED_CELL_BORDER);
        }
        return style.toString();
    }

    private boolean isFocusedCell(TableCell<RowViewModel, ?> cell) {
        if (cell == null || cell.getTableView() == null) {
            return false;
        }
        TablePosition<?, ?> focused = cell.getTableView().getFocusModel() != null
                ? cell.getTableView().getFocusModel().getFocusedCell()
                : null;
        if (focused == null) {
            return false;
        }
        return focused.getRow() == cell.getIndex() && focused.getTableColumn() == cell.getTableColumn();
    }

    private void updateCellStyle(TableCell<RowViewModel, ?> cell) {
        if (cell == null || cell.isEmpty()) {
            if (cell != null) {
                cell.setStyle(null);
            }
            return;
        }
        boolean rowSelected = false;
        if (cell.getTableView() != null && cell.getTableView().getSelectionModel() != null) {
            TableView.TableViewSelectionModel<RowViewModel> selectionModel = cell.getTableView().getSelectionModel();
            if (selectionModel.isCellSelectionEnabled()) {
                rowSelected = selectionModel.isSelected(cell.getIndex(), cell.getTableColumn());
            } else {
                rowSelected = selectionModel.isSelected(cell.getIndex());
            }
        }
        boolean cellFocused = isFocusedCell(cell);
        String bgColor = rowSelected ? SELECTED_ROW_BG : getRowBackground(cell.getIndex());
        cell.setStyle(buildCellStyle(bgColor, cellFocused));
    }

    // ==================== Custom Cell Classes ====================

    /**
     * Text cell with row-based background color and selection highlighting.
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
                updateCellStyle(this);
            }
        }

        @Override
        protected void updateSelected(boolean selected) {
            super.updateSelected(selected);
            updateCellStyle(this);
        }

        @Override
        protected void updateFocused(boolean focused) {
            super.updateFocused(focused);
            updateCellStyle(this);
        }
    }

    /**
     * Number cell with row-based background color and selection highlighting.
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
                updateCellStyle(this);
            }
        }

        @Override
        protected void updateSelected(boolean selected) {
            super.updateSelected(selected);
            updateCellStyle(this);
        }

        @Override
        protected void updateFocused(boolean focused) {
            super.updateFocused(focused);
            updateCellStyle(this);
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
            textFlow.setTextAlignment(TextAlignment.RIGHT);

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
            updateCellStyle(this);

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

        @Override
        protected void updateSelected(boolean selected) {
            super.updateSelected(selected);
            updateCellStyle(this);
        }

        @Override
        protected void updateFocused(boolean focused) {
            super.updateFocused(focused);
            updateCellStyle(this);
        }

        private void formatPrice(double value) {
            String formatted = PRICE_FORMAT.format(value);
            int dot = formatted.indexOf('.');
            if (dot == -1) {
                dot = formatted.indexOf(',');
            }
            if (dot != -1 && formatted.length() >= dot + 6) {
                prefixText.setText(formatted.substring(0, dot + 3));
                highlightText.setText(formatted.substring(dot + 3, dot + 5));
                suffixText.setText(formatted.substring(dot + 5));
            } else {
                prefixText.setText(formatted);
                highlightText.setText("");
                suffixText.setText("");
            }
        }

        private void handleFlashAnimation(double value, int index) {
            boolean recycled = lastIndex != -1 && lastIndex != index;

            if (recycled) {
                flashTimer.stop();
                getStyleClass().removeAll("price-flash");
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
            if (row == null) {
                return;
            }
            String styleClass = row.getStatusStyleClass();
            if (styleClass != null && !styleClass.isEmpty()) {
                getStyleClass().add(styleClass);
            }

            // Apply row background
            updateCellStyle(this);
        }

        @Override
        protected void updateSelected(boolean selected) {
            super.updateSelected(selected);
            updateCellStyle(this);
        }

        @Override
        protected void updateFocused(boolean focused) {
            super.updateFocused(focused);
            updateCellStyle(this);
        }
    }
}

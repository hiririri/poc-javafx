package com.csvmonitor.swing.view;

import com.csvmonitor.swing.controller.MainController;
import com.jidesoft.grid.AutoFilterTableHeader;
import com.jidesoft.grid.FilterableTableModel;
import com.jidesoft.grid.SortableTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main frame for the CSV Table Monitor application.
 * 
 * Features:
 * - Load/save CSV files
 * - Real-time price updates
 * - Status-based row coloring
 * - Column filtering
 * - Sorting
 */
public class MainFrame extends JFrame implements MainView {
    
    private static final Logger logger = LoggerFactory.getLogger(MainFrame.class);
    
    private static final String APP_TITLE = "CSV Table Monitor (Swing)";
    private static final int DEFAULT_WIDTH = 1200;
    private static final int DEFAULT_HEIGHT = 700;
    
    // Components
    private CsvTableModel tableModel;
    private FilterableTableModel filterableTableModel;
    private SortableTable table;
    private Timer priceFlashTimer;
    private JButton startPauseButton;
    private JLabel statusLabel;
    private JLabel rowCountLabel;
    private JPopupMenu columnMenu;
    private final Map<TableColumn, Integer> columnOrder = new HashMap<>();
    
    private MainController controller;
    private Runnable windowCloseHandler;
    
    public MainFrame() {
        setTitle(APP_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        
        initComponents();
        setupWindowListener();
        
        logger.info("MainFrame initialized");
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Toolbar
        add(createToolbar(), BorderLayout.NORTH);
        
        // Table with filter panel
        add(createTablePanel(), BorderLayout.CENTER);
        
        // Status bar
        add(createStatusBar(), BorderLayout.SOUTH);
    }
    
    private JPanel createToolbar() {
        JPanel toolbarContainer = new JPanel();
        toolbarContainer.setLayout(new BoxLayout(toolbarContainer, BoxLayout.Y_AXIS));
        toolbarContainer.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        
        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        JLabel appTitle = new JLabel(APP_TITLE);
        appTitle.setFont(appTitle.getFont().deriveFont(Font.BOLD, 18f));
        titleBar.add(appTitle, BorderLayout.WEST);
        
        rowCountLabel = new JLabel("Rows: 0");
        rowCountLabel.setFont(rowCountLabel.getFont().deriveFont(12f));
        titleBar.add(rowCountLabel, BorderLayout.EAST);
        
        toolbarContainer.add(titleBar);
        toolbarContainer.add(Box.createVerticalStrut(8));
        
        // Button toolbar
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        
        // File buttons
        JButton openCsvButton = new JButton("Open CSV");
        openCsvButton.addActionListener(e -> handleOpenCsv());
        buttonBar.add(openCsvButton);
        
        JButton saveCsvButton = new JButton("Save CSV");
        saveCsvButton.addActionListener(e -> handleSaveCsv());
        buttonBar.add(saveCsvButton);
        
        buttonBar.add(new JSeparator(SwingConstants.VERTICAL));
        
        // Control buttons
        startPauseButton = new JButton("Start");
        startPauseButton.setPreferredSize(new Dimension(100, 26));
        startPauseButton.addActionListener(e -> handleStartPause());
        buttonBar.add(startPauseButton);

        JButton columnsButton = new JButton("Columns");
        columnsButton.addActionListener(e -> showColumnMenu(columnsButton));
        buttonBar.add(columnsButton);

        JButton unlockAllButton = new JButton("Unlock All");
        unlockAllButton.addActionListener(e -> handleUnlockAll());
        buttonBar.add(unlockAllButton);
        
        toolbarContainer.add(buttonBar);
        
        return toolbarContainer;
    }
    
    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        
        // Create table model and filter wrapper
        tableModel = new CsvTableModel();
        filterableTableModel = new FilterableTableModel(tableModel);
        
        // Create table
        table = new SortableTable(filterableTableModel);
//        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setRowHeight(24);
        table.setShowGrid(true);
        table.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(true);
        table.setDragEnabled(true);
        
        // Set column widths
        setColumnWidths();

        // Column chooser menu
        initColumnMenu();
        
        // Apply custom cell renderers for all columns
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(
                    new StatusCellRenderer(tableModel, filterableTableModel, i)
            );
        }
        
        // JIDE auto filter header for per-column filtering
        AutoFilterTableHeader filterHeader = new AutoFilterTableHeader(table);
        filterHeader.setAutoFilterEnabled(true);
        table.setTableHeader(filterHeader);
        
        // Scroll pane with table
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Repaint periodically to animate price flashing.
        priceFlashTimer = new Timer(180, e -> table.repaint());
        priceFlashTimer.start();

        // Listen for data/filter changes to update row count
        filterableTableModel.addTableModelListener(e -> updateRowCount());
        
        return panel;
    }

    private void initColumnMenu() {
        columnMenu = new JPopupMenu();
        TableColumnModel columnModel = table.getColumnModel();
        columnOrder.clear();

        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn column = columnModel.getColumn(i);
            columnOrder.put(column, i);
            String name = tableModel.getColumnName(i);
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name, true);
            item.addActionListener(e -> toggleColumnVisibility(column, item.isSelected()));
            columnMenu.add(item);
        }
    }

    private void showColumnMenu(Component anchor) {
        if (columnMenu != null) {
            columnMenu.show(anchor, 0, anchor.getHeight());
        }
    }

    private void toggleColumnVisibility(TableColumn column, boolean visible) {
        TableColumnModel columnModel = table.getColumnModel();
        boolean isVisible = isColumnVisible(columnModel, column);
        if (visible && !isVisible) {
            columnModel.addColumn(column);
            reorderColumns(columnModel);
        } else if (!visible && isVisible) {
            columnModel.removeColumn(column);
        }
        table.getTableHeader().revalidate();
        table.getTableHeader().repaint();
    }

    private boolean isColumnVisible(TableColumnModel columnModel, TableColumn column) {
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            if (columnModel.getColumn(i) == column) {
                return true;
            }
        }
        return false;
    }

    private void reorderColumns(TableColumnModel columnModel) {
        List<TableColumn> columns = new ArrayList<>();
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            columns.add(columnModel.getColumn(i));
        }
        columns.sort((a, b) -> Integer.compare(
                columnOrder.getOrDefault(a, Integer.MAX_VALUE),
                columnOrder.getOrDefault(b, Integer.MAX_VALUE)
        ));
        for (int i = 0; i < columns.size(); i++) {
            TableColumn column = columns.get(i);
            int currentIndex = columnModel.getColumnIndex(column.getIdentifier());
            if (currentIndex != i) {
                columnModel.moveColumn(currentIndex, i);
            }
        }
    }
    
    private void setColumnWidths() {
        int[] widths = {70, 100, 120, 80, 100, 180};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }
    
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
        
        statusLabel = new JLabel("Ready");
        statusBar.add(statusLabel, BorderLayout.WEST);
        
        JLabel hintLabel = new JLabel("Use column filters to search data");
        hintLabel.setForeground(Color.GRAY);
        statusBar.add(hintLabel, BorderLayout.EAST);
        
        return statusBar;
    }
    
    private void setupWindowListener() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (windowCloseHandler != null) {
                    windowCloseHandler.run();
                }
                if (priceFlashTimer != null) {
                    priceFlashTimer.stop();
                }
            }
        });
    }
    
    // ==================== MainView ====================

    public void setController(MainController controller) {
        this.controller = controller;
    }

    private void handleOpenCsv() {
        if (controller != null) {
            controller.onOpenCsv();
        }
    }

    private void handleSaveCsv() {
        if (controller != null) {
            controller.onSaveCsv();
        }
    }

    private void handleStartPause() {
        if (controller != null) {
            controller.onStartPause();
        }
    }

    private void handleUnlockAll() {
        if (controller != null) {
            controller.onUnlockAll();
        }
    }

    @Override
    public CsvTableModel getTableModel() {
        return tableModel;
    }

    @Override
    public void setStatus(String message) {
        statusLabel.setText(message);
    }

    @Override
    public void setStartPauseLabel(String text) {
        startPauseButton.setText(text);
    }

    @Override
    public void updateRowCount() {
        int total = tableModel.getRowCount();
        int filtered = table.getRowCount();
        
        if (filtered == total) {
            rowCountLabel.setText("Rows: %d".formatted(total));
        } else {
            rowCountLabel.setText("Rows: %d / %d".formatted(filtered, total));
        }
    }

    @Override
    public File promptOpenCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open CSV File");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    @Override
    public File promptSaveCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save CSV File");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        chooser.setSelectedFile(new File("export.csv"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            return file;
        }
        return null;
    }

    @Override
    public void addWindowCloseHandler(Runnable handler) {
        this.windowCloseHandler = handler;
    }
}

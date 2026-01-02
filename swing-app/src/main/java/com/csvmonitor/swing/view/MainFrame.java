package com.csvmonitor.swing.view;

import com.csvmonitor.swing.model.CsvRepository;
import com.csvmonitor.swing.model.RowData;
import com.csvmonitor.swing.model.UpdateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
public class MainFrame extends JFrame {
    
    private static final Logger logger = LoggerFactory.getLogger(MainFrame.class);
    
    private static final String APP_TITLE = "CSV Table Monitor (Swing)";
    private static final int DEFAULT_WIDTH = 1200;
    private static final int DEFAULT_HEIGHT = 700;
    
    // Components
    private CsvTableModel tableModel;
    private JTable table;
    private TableRowSorter<CsvTableModel> rowSorter;
    private FilterPanel filterPanel;
    private JButton startPauseButton;
    private JLabel statusLabel;
    private JLabel rowCountLabel;
    
    // Services
    private final CsvRepository csvRepository = new CsvRepository();
    private final UpdateEngine updateEngine = new UpdateEngine();
    
    // State
    private boolean updateRunning = false;
    
    public MainFrame() {
        setTitle(APP_TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        
        initComponents();
        setupUpdateEngine();
        setupWindowListener();
        
        // Load default CSV on startup
        SwingUtilities.invokeLater(this::loadDefaultCsv);
        
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
        openCsvButton.addActionListener(e -> onOpenCsv());
        buttonBar.add(openCsvButton);
        
        JButton saveCsvButton = new JButton("Save CSV");
        saveCsvButton.addActionListener(e -> onSaveCsv());
        buttonBar.add(saveCsvButton);
        
        buttonBar.add(new JSeparator(SwingConstants.VERTICAL));
        
        // Control buttons
        startPauseButton = new JButton("Start");
        startPauseButton.setPreferredSize(new Dimension(100, 26));
        startPauseButton.addActionListener(e -> onStartPause());
        buttonBar.add(startPauseButton);
        
        JButton unlockAllButton = new JButton("Unlock All");
        unlockAllButton.addActionListener(e -> onUnlockAll());
        buttonBar.add(unlockAllButton);
        
        toolbarContainer.add(buttonBar);
        
        return toolbarContainer;
    }
    
    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        
        // Create table model and sorter
        tableModel = new CsvTableModel();
        rowSorter = new TableRowSorter<>(tableModel);
        
        // Create table
        table = new JTable(tableModel);
        table.setRowSorter(rowSorter);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setRowHeight(24);
        table.setShowGrid(true);
        table.setGridColor(new Color(0xE0, 0xE0, 0xE0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDragEnabled(true);
        
        // Set column widths
        setColumnWidths();
        
        // Apply custom cell renderers for all columns
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(new StatusCellRenderer(tableModel, i));
        }
        
        // Filter panel
        filterPanel = new FilterPanel(table, rowSorter);
        panel.add(filterPanel, BorderLayout.NORTH);
        
        // Scroll pane with table
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Listen for filter changes to update row count
        rowSorter.addRowSorterListener(e -> updateRowCount());
        
        return panel;
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
    
    private void setupUpdateEngine() {
        updateEngine.setTableUpdateCallback(() -> {
            // Trigger table repaint on EDT
            tableModel.fireAllDataUpdated();
        });
    }
    
    private void setupWindowListener() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logger.info("Application closing...");
                updateEngine.shutdown();
            }
        });
    }
    
    // ==================== Event Handlers ====================
    
    private void onOpenCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open CSV File");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            loadCsvFromFile(file);
        }
    }
    
    private void onSaveCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save CSV File");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
        chooser.setSelectedFile(new File("export.csv"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            saveCsvToFile(file);
        }
    }
    
    private void onStartPause() {
        if (updateRunning) {
            pauseUpdates();
        } else {
            startUpdates();
        }
    }
    
    private void onUnlockAll() {
        tableModel.getData().forEach(RowData::unlock);
        setStatus("All rows unlocked");
        logger.info("All rows unlocked");
    }
    
    // ==================== Data Operations ====================
    
    private void loadDefaultCsv() {
        logger.info("Loading default CSV...");
        setStatus("Loading default CSV...");
        
        CompletableFuture.runAsync(() -> {
            List<RowData> data = csvRepository.loadDefaultCsv();
            
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(data);
                updateEngine.setData(tableModel.getData());
                updateRowCount();
                setStatus("Loaded %d rows from sample.csv".formatted(data.size()));
                logger.info("Loaded {} rows from default CSV", data.size());
            });
        }).exceptionally(ex -> {
            logger.error("Failed to load default CSV", ex);
            SwingUtilities.invokeLater(() -> setStatus("Failed to load CSV: " + ex.getMessage()));
            return null;
        });
    }
    
    private void loadCsvFromFile(File file) {
        logger.info("Loading CSV from file: {}", file.getName());
        setStatus("Loading " + file.getName() + "...");
        
        // Pause updates during load
        boolean wasRunning = updateRunning;
        if (wasRunning) {
            pauseUpdates();
        }
        
        final String fileName = file.getName();
        
        CompletableFuture.runAsync(() -> {
            List<RowData> data = csvRepository.loadCsvFromFile(file);
            
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(data);
                updateEngine.setData(tableModel.getData());
                updateRowCount();
                
                if (wasRunning) {
                    startUpdates();
                }
                setStatus("Loaded %d rows from %s".formatted(data.size(), fileName));
                logger.info("Loaded {} rows from {}", data.size(), fileName);
            });
        }).exceptionally(ex -> {
            logger.error("Failed to load CSV from file: {}", fileName, ex);
            SwingUtilities.invokeLater(() -> {
                if (wasRunning) {
                    startUpdates();
                }
                setStatus("Failed to load " + fileName + ": " + ex.getMessage());
            });
            return null;
        });
    }
    
    private void saveCsvToFile(File file) {
        logger.info("Saving CSV to file: {}", file.getName());
        setStatus("Saving to " + file.getName() + "...");
        
        boolean success = csvRepository.saveCsvToFile(tableModel.getData(), file);
        
        if (success) {
            setStatus("Saved %d rows to %s".formatted(tableModel.getRowCount(), file.getName()));
        } else {
            setStatus("Failed to save to " + file.getName());
        }
    }
    
    // ==================== Update Control ====================
    
    private void startUpdates() {
        updateEngine.start();
        updateRunning = true;
        startPauseButton.setText("Pause");
        setStatus("Real-time updates started");
        logger.info("Updates started");
    }
    
    private void pauseUpdates() {
        updateEngine.pause();
        updateRunning = false;
        startPauseButton.setText("Start");
        setStatus("Real-time updates paused");
        logger.info("Updates paused");
    }
    
    // ==================== UI Updates ====================
    
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
    
    private void updateRowCount() {
        int total = tableModel.getRowCount();
        int filtered = table.getRowCount();
        
        if (filtered == total) {
            rowCountLabel.setText("Rows: %d".formatted(total));
        } else {
            rowCountLabel.setText("Rows: %d / %d".formatted(filtered, total));
        }
    }
}


package com.csvmonitor.swing.view;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * Panel containing filter text fields for each column.
 * Provides real-time filtering as user types.
 */
public class FilterPanel extends JPanel {
    
    private final List<JTextField> filterFields = new ArrayList<>();
    private final TableRowSorter<CsvTableModel> rowSorter;
    private final JTable table;
    
    private static final String[] COLUMN_NAMES = {"ID", "Symbol", "Price", "Qty", "Status", "Last Update"};
    
    public FilterPanel(JTable table, TableRowSorter<CsvTableModel> rowSorter) {
        this.table = table;
        this.rowSorter = rowSorter;
        
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("Filters"));
        
        createFilterFields();
    }
    
    private void createFilterFields() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        for (int i = 0; i < COLUMN_NAMES.length; i++) {
            gbc.gridx = i;
            gbc.gridy = 0;
            gbc.weightx = getColumnWeight(i);
            
            JPanel fieldPanel = new JPanel(new BorderLayout(2, 0));
            
            JLabel label = new JLabel(COLUMN_NAMES[i] + ":");
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
            fieldPanel.add(label, BorderLayout.NORTH);
            
            JTextField field = new JTextField();
            field.setPreferredSize(new Dimension(getColumnWidth(i), 24));
            field.setToolTipText("Filter by " + COLUMN_NAMES[i] + " (regex supported)");
            
            final int columnIndex = i;
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) { applyFilters(); }
                @Override
                public void removeUpdate(DocumentEvent e) { applyFilters(); }
                @Override
                public void changedUpdate(DocumentEvent e) { applyFilters(); }
            });
            
            filterFields.add(field);
            fieldPanel.add(field, BorderLayout.CENTER);
            
            add(fieldPanel, gbc);
        }
        
        // Add clear button
        gbc.gridx = COLUMN_NAMES.length;
        gbc.weightx = 0;
        JButton clearButton = new JButton("Clear");
        clearButton.setMargin(new Insets(2, 8, 2, 8));
        clearButton.addActionListener(e -> clearFilters());
        add(clearButton, gbc);
    }
    
    private double getColumnWeight(int columnIndex) {
        return switch (columnIndex) {
            case 0 -> 0.5;   // ID
            case 1 -> 1.0;   // Symbol
            case 2 -> 0.8;   // Price
            case 3 -> 0.6;   // Qty
            case 4 -> 0.8;   // Status
            case 5 -> 1.5;   // LastUpdate
            default -> 1.0;
        };
    }
    
    private int getColumnWidth(int columnIndex) {
        return switch (columnIndex) {
            case 0 -> 60;    // ID
            case 1 -> 80;    // Symbol
            case 2 -> 70;    // Price
            case 3 -> 60;    // Qty
            case 4 -> 70;    // Status
            case 5 -> 120;   // LastUpdate
            default -> 80;
        };
    }
    
    /**
     * Apply filters from all filter fields.
     */
    private void applyFilters() {
        List<RowFilter<CsvTableModel, Object>> filters = new ArrayList<>();
        
        for (int i = 0; i < filterFields.size(); i++) {
            String text = filterFields.get(i).getText().trim();
            if (!text.isEmpty()) {
                try {
                    final int column = i;
                    // Case insensitive regex filter
                    RowFilter<CsvTableModel, Object> rf = RowFilter.regexFilter("(?i)" + text, column);
                    filters.add(rf);
                } catch (PatternSyntaxException e) {
                    // Invalid regex, ignore this filter
                }
            }
        }
        
        if (filters.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }
    
    /**
     * Clear all filters.
     */
    public void clearFilters() {
        for (JTextField field : filterFields) {
            field.setText("");
        }
        rowSorter.setRowFilter(null);
    }
    
    /**
     * Get the number of visible (filtered) rows.
     */
    public int getFilteredRowCount() {
        return table.getRowCount();
    }
}


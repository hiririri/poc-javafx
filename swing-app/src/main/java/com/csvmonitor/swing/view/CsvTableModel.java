package com.csvmonitor.swing.view;

import com.csvmonitor.swing.model.RowData;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Swing TableModel for CSV data display.
 * Provides column definitions and data access for JTable.
 */
public class CsvTableModel extends AbstractTableModel {
    
    private static final String[] COLUMN_NAMES = {"ID", "Symbol", "Price", "Qty", "Status", "Last Update"};
    private static final Class<?>[] COLUMN_CLASSES = {Integer.class, String.class, Double.class, Integer.class, String.class, String.class};
    
    private final List<RowData> data = new ArrayList<>();
    
    @Override
    public int getRowCount() {
        return data.size();
    }
    
    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }
    
    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }
    
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }
    
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
    
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= data.size()) {
            return null;
        }
        
        RowData row = data.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.getId();
            case 1 -> row.getSymbol();
            case 2 -> row.getPrice();
            case 3 -> row.getQty();
            case 4 -> row.getStatus();
            case 5 -> row.getLastUpdate();
            default -> null;
        };
    }
    
    /**
     * Get the RowData at the specified row index.
     */
    public RowData getRowAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= data.size()) {
            return null;
        }
        return data.get(rowIndex);
    }
    
    /**
     * Get all data.
     */
    public List<RowData> getData() {
        return data;
    }
    
    /**
     * Set the data for the table.
     */
    public void setData(List<RowData> newData) {
        data.clear();
        if (newData != null) {
            data.addAll(newData);
        }
        fireTableDataChanged();
    }
    
    /**
     * Clear all data.
     */
    public void clearData() {
        data.clear();
        fireTableDataChanged();
    }
    
    /**
     * Add rows in batch.
     */
    public void addRows(List<RowData> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int firstRow = data.size();
        data.addAll(rows);
        fireTableRowsInserted(firstRow, data.size() - 1);
    }
    
    /**
     * Notify that specific rows have been updated.
     */
    public void fireRowsUpdated(int firstRow, int lastRow) {
        if (firstRow >= 0 && lastRow < data.size()) {
            fireTableRowsUpdated(firstRow, lastRow);
        }
    }
    
    /**
     * Notify that all data has been updated (for batch refresh).
     */
    public void fireAllDataUpdated() {
        if (!data.isEmpty()) {
            fireTableRowsUpdated(0, data.size() - 1);
        }
    }
}


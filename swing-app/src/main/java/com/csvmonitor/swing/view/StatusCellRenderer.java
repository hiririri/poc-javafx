package com.csvmonitor.swing.view;

import com.csvmonitor.swing.model.RowData;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Custom cell renderer that applies background color based on row status.
 * This renderer colors the entire row based on the status value.
 */
public class StatusCellRenderer extends DefaultTableCellRenderer {
    
    // Background colors for each status type
    private static final Color ALERT_BG = new Color(0xFF, 0xCC, 0xCC);    // Light red
    private static final Color NORMAL_BG = new Color(0xE6, 0xFF, 0xE6);   // Light green
    private static final Color PENDING_BG = new Color(0xFF, 0xF2, 0xCC);  // Light orange
    private static final Color ACTIVE_BG = new Color(0xCC, 0xE6, 0xFF);   // Light blue
    private static final Color CLOSED_BG = new Color(0xE6, 0xE6, 0xE6);   // Light gray
    
    // Text colors for status column
    private static final Color ALERT_FG = new Color(0xCC, 0x00, 0x00);    // Dark red
    private static final Color NORMAL_FG = new Color(0x00, 0x88, 0x00);   // Dark green
    private static final Color PENDING_FG = new Color(0xCC, 0x88, 0x00);  // Dark orange
    private static final Color ACTIVE_FG = new Color(0x00, 0x66, 0xCC);   // Dark blue
    private static final Color CLOSED_FG = new Color(0x66, 0x66, 0x66);   // Dark gray
    
    private final CsvTableModel tableModel;
    private final int columnIndex;
    private final boolean isStatusColumn;
    private final boolean isPriceColumn;
    
    public StatusCellRenderer(CsvTableModel tableModel, int columnIndex) {
        this.tableModel = tableModel;
        this.columnIndex = columnIndex;
        this.isStatusColumn = columnIndex == 4;  // Status column
        this.isPriceColumn = columnIndex == 2;   // Price column
        
        // Set alignment based on column type
        switch (columnIndex) {
            case 0 -> setHorizontalAlignment(SwingConstants.CENTER);  // ID
            case 1 -> setHorizontalAlignment(SwingConstants.LEFT);    // Symbol
            case 2 -> setHorizontalAlignment(SwingConstants.RIGHT);   // Price
            case 3 -> setHorizontalAlignment(SwingConstants.RIGHT);   // Qty
            case 4 -> setHorizontalAlignment(SwingConstants.CENTER);  // Status
            case 5 -> setHorizontalAlignment(SwingConstants.LEFT);    // LastUpdate
        }
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        
        // Convert view row to model row (important for sorting/filtering)
        int modelRow = table.convertRowIndexToModel(row);
        RowData rowData = tableModel.getRowAt(modelRow);
        
        if (rowData == null) {
            return c;
        }
        
        String status = rowData.getStatus();
        
        if (isSelected) {
            // Keep selection colors but slightly tint with status color
            Color statusBg = getStatusBackgroundColor(status);
            if (statusBg != null) {
                c.setBackground(blend(table.getSelectionBackground(), statusBg, 0.7f));
            }
        } else {
            // Apply status background color
            Color bgColor = getStatusBackgroundColor(status);
            c.setBackground(bgColor != null ? bgColor : table.getBackground());
            
            // Apply special styling for status column
            if (isStatusColumn) {
                c.setForeground(getStatusForegroundColor(status));
                if (c instanceof JLabel label) {
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                }
            } else if (isPriceColumn) {
                // Price direction coloring
                int direction = rowData.getPriceDirection();
                if (direction > 0) {
                    c.setForeground(NORMAL_FG);  // Green for up
                } else if (direction < 0) {
                    c.setForeground(ALERT_FG);   // Red for down
                } else {
                    c.setForeground(table.getForeground());
                }
            } else {
                c.setForeground(table.getForeground());
            }
        }
        
        // Format price with 5 decimal places
        if (isPriceColumn && value instanceof Double price) {
            setText(String.format("%.5f", price));
        }
        
        return c;
    }
    
    private Color getStatusBackgroundColor(String status) {
        if (status == null) return null;
        
        return switch (status.toUpperCase()) {
            case "ALERT", "WARN", "WARNING" -> ALERT_BG;
            case "NORMAL", "OK", "GOOD" -> NORMAL_BG;
            case "PENDING", "WAIT" -> PENDING_BG;
            case "ACTIVE", "LIVE", "RUNNING" -> ACTIVE_BG;
            case "CLOSED", "DONE", "COMPLETE" -> CLOSED_BG;
            default -> null;
        };
    }
    
    private Color getStatusForegroundColor(String status) {
        if (status == null) return Color.BLACK;
        
        return switch (status.toUpperCase()) {
            case "ALERT", "WARN", "WARNING" -> ALERT_FG;
            case "NORMAL", "OK", "GOOD" -> NORMAL_FG;
            case "PENDING", "WAIT" -> PENDING_FG;
            case "ACTIVE", "LIVE", "RUNNING" -> ACTIVE_FG;
            case "CLOSED", "DONE", "COMPLETE" -> CLOSED_FG;
            default -> Color.BLACK;
        };
    }
    
    /**
     * Blend two colors together.
     */
    private Color blend(Color c1, Color c2, float ratio) {
        float iRatio = 1.0f - ratio;
        int r = (int) (c1.getRed() * ratio + c2.getRed() * iRatio);
        int g = (int) (c1.getGreen() * ratio + c2.getGreen() * iRatio);
        int b = (int) (c1.getBlue() * ratio + c2.getBlue() * iRatio);
        return new Color(r, g, b);
    }
}


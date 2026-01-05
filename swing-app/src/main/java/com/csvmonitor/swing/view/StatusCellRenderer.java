package com.csvmonitor.swing.view;

import com.csvmonitor.swing.model.RowData;
import com.jidesoft.grid.FilterableTableModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.lang.reflect.Method;

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
    private static final Color PRICE_FLASH_A = new Color(0xFF, 0xF4, 0xB5); // Light amber
    private static final Color PRICE_FLASH_B = new Color(0xFF, 0xE0, 0x8A); // Deeper amber
    private static final long PRICE_FLASH_WINDOW_MS = 1000;
    private static final long PRICE_FLASH_TOGGLE_MS = 180;
    
    // Text colors for status column
    private static final Color ALERT_FG = new Color(0xCC, 0x00, 0x00);    // Dark red
    private static final Color NORMAL_FG = new Color(0x00, 0x88, 0x00);   // Dark green
    private static final Color PENDING_FG = new Color(0xCC, 0x88, 0x00);  // Dark orange
    private static final Color ACTIVE_FG = new Color(0x00, 0x66, 0xCC);   // Dark blue
    private static final Color CLOSED_FG = new Color(0x66, 0x66, 0x66);   // Dark gray
    
    private final CsvTableModel tableModel;
    private final FilterableTableModel filterableTableModel;
    private final int columnIndex;
    private final boolean isStatusColumn;
    private final boolean isPriceColumn;
    private final Method actualRowMethod;
    
    public StatusCellRenderer(CsvTableModel tableModel, FilterableTableModel filterableTableModel, int columnIndex) {
        this.tableModel = tableModel;
        this.filterableTableModel = filterableTableModel;
        this.columnIndex = columnIndex;
        this.isStatusColumn = columnIndex == 4;  // Status column
        this.isPriceColumn = columnIndex == 2;   // Price column
        this.actualRowMethod = resolveActualRowMethod();
        
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
        
        RowData rowData = resolveRowData(table, row);
        
        if (rowData == null) {
            return c;
        }
        
        applyBackground(table, c, rowData, isSelected);
        applyForegroundAndFont(table, c, rowData, isSelected);
        applyPriceFormatting(c, value);
        
        return c;
    }

    private RowData resolveRowData(JTable table, int viewRow) {
        int modelRow = table.convertRowIndexToModel(viewRow);
        int actualRow = resolveActualRow(modelRow);
        return tableModel.getRowAt(actualRow);
    }

    private void applyBackground(JTable table, Component c, RowData rowData, boolean isSelected) {
        if (isSelected) {
            Color statusBg = getStatusBackgroundColor(rowData.getStatus());
            if (statusBg != null) {
                c.setBackground(blend(table.getSelectionBackground(), statusBg, 0.7f));
            }
            return;
        }

        if (isPriceColumn && shouldFlashPrice(rowData)) {
            c.setBackground(getFlashColor());
            return;
        }

        Color bgColor = getStatusBackgroundColor(rowData.getStatus());
        c.setBackground(bgColor != null ? bgColor : table.getBackground());
    }

    private void applyForegroundAndFont(JTable table, Component c, RowData rowData, boolean isSelected) {
        if (isSelected) {
            return;
        }
        if (isStatusColumn) {
            c.setForeground(getStatusForegroundColor(rowData.getStatus()));
            if (c instanceof JLabel label) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            return;
        }
        if (isPriceColumn) {
            c.setForeground(getPriceForegroundColor(rowData, table));
            return;
        }
        c.setForeground(table.getForeground());
    }

    private void applyPriceFormatting(Component c, Object value) {
        if (!isPriceColumn || !(value instanceof Double price)) {
            return;
        }
        if (c instanceof JLabel label) {
            label.setText(formatPriceWithEmphasis(price));
        } else {
            setText(String.format("%.5f", price));
        }
    }

    private boolean shouldFlashPrice(RowData rowData) {
        return System.currentTimeMillis() - rowData.getLastPriceChangeAt() < PRICE_FLASH_WINDOW_MS;
    }

    private Color getFlashColor() {
        boolean phase = (System.currentTimeMillis() / PRICE_FLASH_TOGGLE_MS) % 2 == 0;
        return phase ? PRICE_FLASH_A : PRICE_FLASH_B;
    }

    private Color getPriceForegroundColor(RowData rowData, JTable table) {
        int direction = rowData.getPriceDirection();
        if (direction > 0) {
            return NORMAL_FG;
        }
        if (direction < 0) {
            return ALERT_FG;
        }
        return table.getForeground();
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

    private String formatPriceWithEmphasis(double price) {
        String formatted = String.format("%.5f", price);
        int dotIndex = formatted.indexOf('.');
        if (dotIndex < 0) {
            return formatted;
        }
        String intPart = formatted.substring(0, dotIndex + 1);
        String frac = formatted.substring(dotIndex + 1);
        if (frac.length() < 4) {
            return formatted;
        }
        String firstTwo = frac.substring(0, 2);
        String emph = frac.substring(2, 4);
        String rest = frac.substring(4);
        return "<html>" + intPart + firstTwo
                + "<b><span style='font-size:110%'>" + emph + "</span></b>"
                + rest + "</html>";
    }

    private Method resolveActualRowMethod() {
        if (filterableTableModel == null) {
            return null;
        }
        for (String name : new String[]{"getActualRow", "getActualRowAt", "getActualRowIndex"}) {
            try {
                return filterableTableModel.getClass().getMethod(name, int.class);
            } catch (NoSuchMethodException ignored) {
                // Try the next possible method name.
            }
        }
        return null;
    }

    private int resolveActualRow(int modelRow) {
        if (filterableTableModel == null || actualRowMethod == null) {
            return modelRow;
        }
        try {
            Object value = actualRowMethod.invoke(filterableTableModel, modelRow);
            if (value instanceof Integer actualRow) {
                return actualRow;
            }
        } catch (Exception ignored) {
            // Fall back to model row when the reflective call fails.
        }
        return modelRow;
    }
}

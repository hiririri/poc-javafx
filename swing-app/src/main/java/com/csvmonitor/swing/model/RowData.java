package com.csvmonitor.swing.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model class representing a single row in the CSV table.
 * Uses PropertyChangeSupport for change notifications (Swing's equivalent of JavaFX Properties).
 * 
 * Key features:
 * - All fields support property change events for real-time UI updates
 * - Tracks previous price for up/down styling
 * - Supports edit locking mechanism (5 seconds after manual edit)
 */
public class RowData {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final long LOCK_DURATION_MS = 5000;
    
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    private int id;
    private String symbol;
    private double price;
    private double previousPrice;
    private int qty;
    private String status;
    private String lastUpdate;
    private long editLockUntil;
    private long lastPriceChangeAt;
    
    public RowData() {
    }
    
    public RowData(int id, String symbol, double price, int qty, String status, String lastUpdate) {
        this.id = id;
        this.symbol = symbol;
        this.price = price;
        this.previousPrice = price;
        this.qty = qty;
        this.status = status;
        this.lastUpdate = lastUpdate;
    }
    
    // Property change support
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }
    
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
    
    // ID property
    public int getId() { return id; }
    public void setId(int value) {
        int oldValue = this.id;
        this.id = value;
        pcs.firePropertyChange("id", oldValue, value);
    }
    
    // Symbol property
    public String getSymbol() { return symbol; }
    public void setSymbol(String value) {
        String oldValue = this.symbol;
        this.symbol = value;
        pcs.firePropertyChange("symbol", oldValue, value);
    }
    
    // Price property with previous price tracking
    public double getPrice() { return price; }
    public void setPrice(double value) {
        double oldValue = this.price;
        this.previousPrice = this.price;
        this.price = value;
        if (Double.compare(oldValue, value) != 0) {
            lastPriceChangeAt = System.currentTimeMillis();
        }
        pcs.firePropertyChange("price", oldValue, value);
    }
    
    // Previous price for tracking changes
    public double getPreviousPrice() { return previousPrice; }
    public void setPreviousPrice(double value) { previousPrice = value; }

    public long getLastPriceChangeAt() { return lastPriceChangeAt; }
    
    // Qty property
    public int getQty() { return qty; }
    public void setQty(int value) {
        int oldValue = this.qty;
        this.qty = value;
        pcs.firePropertyChange("qty", oldValue, value);
    }
    
    // Status property
    public String getStatus() { return status; }
    public void setStatus(String value) {
        String oldValue = this.status;
        this.status = value;
        pcs.firePropertyChange("status", oldValue, value);
    }
    
    // LastUpdate property
    public String getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(String value) {
        String oldValue = this.lastUpdate;
        this.lastUpdate = value;
        pcs.firePropertyChange("lastUpdate", oldValue, value);
    }
    
    // Edit lock mechanism
    public long getEditLockUntil() { return editLockUntil; }
    public void setEditLockUntil(long value) { editLockUntil = value; }
    
    /**
     * Lock this row from automatic updates for 5 seconds.
     */
    public void lockForEdit() {
        setEditLockUntil(System.currentTimeMillis() + LOCK_DURATION_MS);
    }
    
    /**
     * Unlock this row immediately.
     */
    public void unlock() {
        setEditLockUntil(0);
    }
    
    /**
     * Check if this row is currently locked.
     */
    public boolean isLocked() {
        return System.currentTimeMillis() < editLockUntil;
    }
    
    /**
     * Get price change direction.
     * @return 1 for up, -1 for down, 0 for no change
     */
    public int getPriceDirection() {
        if (price > previousPrice) return 1;
        if (price < previousPrice) return -1;
        return 0;
    }
    
    /**
     * Update the lastUpdate timestamp to current time.
     */
    public void updateTimestamp() {
        setLastUpdate(LocalDateTime.now().format(ISO_FORMATTER));
    }
    
    @Override
    public String toString() {
        return String.format("RowData{id=%d, symbol='%s', price=%.2f, qty=%d, status='%s'}",
                id, symbol, price, qty, status);
    }
}

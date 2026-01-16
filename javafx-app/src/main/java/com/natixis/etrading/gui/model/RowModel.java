package com.natixis.etrading.gui.model;

import javafx.beans.property.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RowModel {
    
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final long LOCK_DURATION_MS = 5000;
    
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty symbol = new SimpleStringProperty();
    private final DoubleProperty price = new SimpleDoubleProperty();
    private final IntegerProperty qty = new SimpleIntegerProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final StringProperty lastUpdate = new SimpleStringProperty();
    
    // Track previous price for conditional formatting (up/down arrows)
    private final DoubleProperty previousPrice = new SimpleDoubleProperty();
    
    // Edit lock mechanism: when user manually edits, lock for 5 seconds
    private final LongProperty editLockUntil = new SimpleLongProperty(0);
    
    public RowModel() {
    }
    
    public RowModel(int id, String symbol, double price, int qty, String status, String lastUpdate) {
        setId(id);
        setSymbol(symbol);
        setPrice(price);
        setPreviousPrice(price); // Initialize previous price same as current
        setQty(qty);
        setStatus(status);
        setLastUpdate(lastUpdate);
    }
    
    // ID property
    public int getId() { return id.get(); }
    public void setId(int value) { id.set(value); }
    public IntegerProperty idProperty() { return id; }
    
    // Symbol property
    public String getSymbol() { return symbol.get(); }
    public void setSymbol(String value) { symbol.set(value); }
    public StringProperty symbolProperty() { return symbol; }
    
    // Price property with previous price tracking
    public double getPrice() { return price.get(); }
    public void setPrice(double value) {
        // Store current price as previous before updating
        setPreviousPrice(getPrice());
        price.set(value);
    }
    public DoubleProperty priceProperty() { return price; }
    
    // Previous price for tracking changes
    public double getPreviousPrice() { return previousPrice.get(); }
    public void setPreviousPrice(double value) { previousPrice.set(value); }
    public DoubleProperty previousPriceProperty() { return previousPrice; }
    
    // Qty property
    public int getQty() { return qty.get(); }
    public void setQty(int value) { qty.set(value); }
    public IntegerProperty qtyProperty() { return qty; }
    
    // Status property
    public String getStatus() { return status.get(); }
    public void setStatus(String value) { status.set(value); }
    public StringProperty statusProperty() { return status; }
    
    // LastUpdate property
    public String getLastUpdate() { return lastUpdate.get(); }
    public void setLastUpdate(String value) { lastUpdate.set(value); }
    public StringProperty lastUpdateProperty() { return lastUpdate; }
    
    // Edit lock mechanism
    public long getEditLockUntil() { return editLockUntil.get(); }
    public void setEditLockUntil(long value) { editLockUntil.set(value); }
    public LongProperty editLockUntilProperty() { return editLockUntil; }
    
    /**
     * Lock this row from automatic updates for 5 seconds.
     * Called after manual price edit.
     */
    public void lockForEdit() {
        setEditLockUntil(System.currentTimeMillis() + LOCK_DURATION_MS);
    }
    
    /**
     * Unlock this row immediately, allowing automatic updates.
     */
    public void unlock() {
        setEditLockUntil(0);
    }
    
    /**
     * Check if this row is currently locked from automatic updates.
     */
    public boolean isLocked() {
        return System.currentTimeMillis() < getEditLockUntil();
    }
    
    /**
     * Get price change direction.
     * @return 1 for up, -1 for down, 0 for no change
     */
    public int getPriceDirection() {
        double current = getPrice();
        double previous = getPreviousPrice();
        if (current > previous) return 1;
        if (current < previous) return -1;
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
        return String.format("RowModel{id=%d, symbol='%s', price=%.2f, qty=%d, status='%s'}",
                getId(), getSymbol(), getPrice(), getQty(), getStatus());
    }
}


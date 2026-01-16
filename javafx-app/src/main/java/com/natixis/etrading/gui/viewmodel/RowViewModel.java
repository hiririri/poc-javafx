package com.natixis.etrading.gui.viewmodel;

import com.natixis.etrading.gui.model.RowModel;
import javafx.beans.property.*;

/**
 * ViewModel wrapper for RowModel.
 * Exposes only the necessary properties to the View layer.
 * Contains presentation logic (styling, formatting).
 */
public class RowViewModel {

    private final RowModel model;

    // Expose properties for binding
    private final ReadOnlyIntegerWrapper id;
    private final ReadOnlyStringWrapper symbol;
    private final ReadOnlyDoubleWrapper price;
    private final ReadOnlyIntegerWrapper qty;
    private final ReadOnlyStringWrapper status;
    private final ReadOnlyStringWrapper lastUpdate;

    // Presentation properties
    private final ReadOnlyStringWrapper priceStyleClass = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper statusStyleClass = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper rowStyleClass = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper formattedPrice = new ReadOnlyStringWrapper("");

    public RowViewModel(RowModel model) {
        this.model = model;

        // Wrap model properties as read-only
        this.id = new ReadOnlyIntegerWrapper();
        this.id.bind(model.idProperty());

        this.symbol = new ReadOnlyStringWrapper();
        this.symbol.bind(model.symbolProperty());

        this.price = new ReadOnlyDoubleWrapper();
        this.price.bind(model.priceProperty());

        this.qty = new ReadOnlyIntegerWrapper();
        this.qty.bind(model.qtyProperty());

        this.status = new ReadOnlyStringWrapper();
        this.status.bind(model.statusProperty());

        this.lastUpdate = new ReadOnlyStringWrapper();
        this.lastUpdate.bind(model.lastUpdateProperty());

        // Update presentation properties when data changes
        model.priceProperty().addListener((obs, oldVal, newVal) -> updatePriceStyle());
        model.statusProperty().addListener((obs, oldVal, newVal) -> updateStatusStyle());

        // Initialize styles
        updatePriceStyle();
        updateStatusStyle();
        updateFormattedPrice();
    }

    // ==================== Presentation Logic ====================

    /**
     * Update price style class based on direction.
     */
    private void updatePriceStyle() {
        int direction = model.getPriceDirection();
        String style = switch (direction) {
            case 1 -> "price-up";
            case -1 -> "price-down";
            default -> "";
        };
        priceStyleClass.set(style);
        updateFormattedPrice();
    }

    /**
     * Update status style class.
     */
    private void updateStatusStyle() {
        String statusValue = model.getStatus();
        if (statusValue == null) {
            statusStyleClass.set("");
            rowStyleClass.set("");
            return;
        }

        String upperStatus = statusValue.toUpperCase();
        
        // Cell style for status column
        String cellStyle = switch (upperStatus) {
            case "ALERT", "WARN", "WARNING" -> "status-alert";
            case "NORMAL", "OK", "GOOD" -> "status-normal";
            case "PENDING", "WAIT" -> "status-pending";
            case "ACTIVE", "LIVE", "RUNNING" -> "status-active";
            case "CLOSED", "DONE", "COMPLETE" -> "status-closed";
            default -> "";
        };
        statusStyleClass.set(cellStyle);

        // Row background style based on status
        String rowStyle = switch (upperStatus) {
            case "ALERT", "WARN", "WARNING" -> "row-alert";
            case "NORMAL", "OK", "GOOD" -> "row-normal";
            case "PENDING", "WAIT" -> "row-pending";
            case "ACTIVE", "LIVE", "RUNNING" -> "row-active";
            case "CLOSED", "DONE", "COMPLETE" -> "row-closed";
            default -> "";
        };
        rowStyleClass.set(rowStyle);
    }

    /**
     * Update formatted price string.
     */
    private void updateFormattedPrice() {
        formattedPrice.set(String.format("%.2f", model.getPrice()));
    }

    // ==================== Read-Only Properties for View ====================

    public ReadOnlyIntegerProperty idProperty() {
        return id.getReadOnlyProperty();
    }

    public int getId() {
        return id.get();
    }

    public ReadOnlyStringProperty symbolProperty() {
        return symbol.getReadOnlyProperty();
    }

    public String getSymbol() {
        return symbol.get();
    }

    public ReadOnlyDoubleProperty priceProperty() {
        return price.getReadOnlyProperty();
    }

    public double getPrice() {
        return price.get();
    }

    public ReadOnlyIntegerProperty qtyProperty() {
        return qty.getReadOnlyProperty();
    }

    public int getQty() {
        return qty.get();
    }

    public ReadOnlyStringProperty statusProperty() {
        return status.getReadOnlyProperty();
    }

    public String getStatus() {
        return status.get();
    }

    public ReadOnlyStringProperty lastUpdateProperty() {
        return lastUpdate.getReadOnlyProperty();
    }

    public String getLastUpdate() {
        return lastUpdate.get();
    }

    // ==================== Presentation Properties ====================

    public ReadOnlyStringProperty priceStyleClassProperty() {
        return priceStyleClass.getReadOnlyProperty();
    }

    public String getPriceStyleClass() {
        return priceStyleClass.get();
    }

    public ReadOnlyStringProperty statusStyleClassProperty() {
        return statusStyleClass.getReadOnlyProperty();
    }

    public String getStatusStyleClass() {
        return statusStyleClass.get();
    }

    public ReadOnlyStringProperty rowStyleClassProperty() {
        return rowStyleClass.getReadOnlyProperty();
    }

    public String getRowStyleClass() {
        return rowStyleClass.get();
    }

    public ReadOnlyStringProperty formattedPriceProperty() {
        return formattedPrice.getReadOnlyProperty();
    }

    public String getFormattedPrice() {
        return formattedPrice.get();
    }

    /**
     * Check if row is locked (for conditional styling).
     */
    public boolean isLocked() {
        return model.isLocked();
    }


    public int getPriceDirection() {
        return model.getPriceDirection();
    }

    /**
     * Get the underlying model (package-private, for ViewModel use only).
     */
    RowModel getModel() {
        return model;
    }
}


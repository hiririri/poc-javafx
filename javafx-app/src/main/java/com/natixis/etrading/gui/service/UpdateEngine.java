package com.natixis.etrading.gui.service;

import com.natixis.etrading.gui.model.RowModel;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(UpdateEngine.class);
    
    // Java 21: Use virtual thread factory for the scheduler
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, 
            Thread.ofVirtual().name("UpdateEngine-VT-", 0).factory());
    
    private final Random random = new Random();
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private ScheduledFuture<?> updateTask;
    private ObservableList<RowModel> data;
    
    // Configuration using record for immutability
    private UpdateConfig config = new UpdateConfig(500, 5, 1000, 0.80);
    

    public record UpdateConfig(
            long intervalMs,
            int minRowsToUpdate,
            int maxRowsToUpdate,
            double priceChangePercent
    ) {
        // Compact constructor with validation
        public UpdateConfig {
            if (intervalMs <= 0) throw new IllegalArgumentException("intervalMs must be positive");
            if (minRowsToUpdate < 0) throw new IllegalArgumentException("minRowsToUpdate must be non-negative");
            if (maxRowsToUpdate < minRowsToUpdate) throw new IllegalArgumentException("maxRowsToUpdate must be >= minRowsToUpdate");
            if (priceChangePercent < 0) throw new IllegalArgumentException("priceChangePercent must be non-negative");
        }
    }
    
    /**
     * Internal record to hold pending price updates.
     */
    private record PriceUpdate(RowModel row, double newPrice) {}
    
    /**
     * Set the data source for updates.
     */
    public void setData(ObservableList<RowModel> data) {
        this.data = data;
        logger.info("UpdateEngine data source set with {} rows", data != null ? data.size() : 0);
    }
    
    /**
     * Update configuration.
     */
    public void setConfig(UpdateConfig config) {
        this.config = config;
    }
    
    /**
     * Start the real-time update engine.
     */
    public void start() {
        if (data == null || data.isEmpty()) {
            logger.warn("Cannot start UpdateEngine: no data available");
            return;
        }
        
        if (running.compareAndSet(false, true)) {
            logger.info("Starting UpdateEngine with {}ms interval (Virtual Threads)", config.intervalMs());
            
            updateTask = scheduler.scheduleAtFixedRate(
                    this::performUpdate,
                    config.intervalMs(),
                    config.intervalMs(),
                    TimeUnit.MILLISECONDS
            );
        } else {
            logger.debug("UpdateEngine already running");
        }
    }
    
    /**
     * Pause the real-time update engine.
     */
    public void pause() {
        if (running.compareAndSet(true, false)) {
            if (updateTask != null) {
                updateTask.cancel(false);
                updateTask = null;
            }
            logger.info("UpdateEngine paused");
        }
    }
    
    /**
     * Check if engine is currently running.
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Shutdown the engine completely.
     */
    public void shutdown() {
        pause();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("UpdateEngine shutdown complete");
    }
    
    /**
     * Perform a batch update on random rows.
     * Only updates the price column.
     */
    private void performUpdate() {
        if (data == null || data.isEmpty()) {
            return;
        }
        
        try {
            int dataSize = data.size();
            int rowsToUpdate = config.minRowsToUpdate() + 
                    random.nextInt(config.maxRowsToUpdate() - config.minRowsToUpdate() + 1);
            rowsToUpdate = Math.min(rowsToUpdate, dataSize);
            
            // Collect price updates using Stream API
            List<PriceUpdate> updates = new ArrayList<>(rowsToUpdate);
            
            for (int i = 0; i < rowsToUpdate; i++) {
                int index = random.nextInt(dataSize);
                RowModel row = data.get(index);
                
                // Skip locked rows
                if (row.isLocked()) {
                    continue;
                }
                
                // Generate new price
                double currentPrice = row.getPrice();
                double priceChange = currentPrice * config.priceChangePercent() * (random.nextDouble() * 2 - 1);
                double newPrice = Math.max(0.01, currentPrice + priceChange);
                newPrice = Math.round(newPrice * 1000000.0) / 1000000.0;
                
                updates.add(new PriceUpdate(row, newPrice));
            }
            
            // Apply updates on JavaFX thread
            if (!updates.isEmpty()) {
                Platform.runLater(() -> applyUpdates(updates));
            }
            
        } catch (Exception e) {
            logger.error("Error during update: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Apply price updates to the model.
     * Uses record accessor methods for clean code.
     */
    private void applyUpdates(List<PriceUpdate> updates) {
        updates.stream()
                .filter(update -> !update.row().isLocked())
                .forEach(update -> update.row().setPrice(update.newPrice()));
        
        logger.trace("Applied {} price updates", updates.size());
    }
    
    // Legacy setters for backward compatibility
    public void setUpdateIntervalMs(long intervalMs) {
        this.config = new UpdateConfig(intervalMs, config.minRowsToUpdate(), 
                config.maxRowsToUpdate(), config.priceChangePercent());
    }
    
    public void setRowsToUpdateRange(int min, int max) {
        this.config = new UpdateConfig(config.intervalMs(), min, max, config.priceChangePercent());
    }
    
    public void setPriceChangePercent(double percent) {
        this.config = new UpdateConfig(config.intervalMs(), config.minRowsToUpdate(),
                config.maxRowsToUpdate(), percent);
    }
}

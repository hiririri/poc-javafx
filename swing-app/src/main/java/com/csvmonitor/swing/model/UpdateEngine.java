package com.csvmonitor.swing.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Engine for real-time price updates.
 * 
 * Key features:
 * - Runs on background thread, updates UI via SwingUtilities.invokeLater
 * - Respects edit locks (rows manually edited are not auto-updated for 5 seconds)
 * - Batch updates for performance
 */
public class UpdateEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(UpdateEngine.class);
    
    // Use virtual thread factory for the scheduler (Java 21)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, 
            Thread.ofVirtual().name("UpdateEngine-VT-", 0).factory());
    
    private final Random random = new Random();
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private ScheduledFuture<?> updateTask;
    private List<RowData> data;
    private Runnable tableUpdateCallback;
    
    // Configuration
    private UpdateConfig config = new UpdateConfig(500, 5, 1000, 0.20);
    
    /**
     * Configuration record for update parameters.
     */
    public record UpdateConfig(
            long intervalMs,
            int minRowsToUpdate,
            int maxRowsToUpdate,
            double priceChangePercent
    ) {
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
    private record PriceUpdate(RowData row, double newPrice) {}
    
    /**
     * Set the data source for updates.
     */
    public void setData(List<RowData> data) {
        this.data = data;
        logger.info("UpdateEngine data source set with {} rows", data != null ? data.size() : 0);
    }
    
    /**
     * Set callback for table updates (to trigger table repaint).
     */
    public void setTableUpdateCallback(Runnable callback) {
        this.tableUpdateCallback = callback;
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
            
            // Collect price updates
            List<PriceUpdate> updates = new ArrayList<>(rowsToUpdate);
            
            for (int i = 0; i < rowsToUpdate; i++) {
                int index = random.nextInt(dataSize);
                RowData row = data.get(index);
                
                // Skip locked rows
                if (row.isLocked()) {
                    continue;
                }
                
                // Generate new price
                double currentPrice = row.getPrice();
                double priceChange = currentPrice * config.priceChangePercent() * (random.nextDouble() * 2 - 1);
                double newPrice = Math.max(0.01, currentPrice + priceChange);
                newPrice = Math.round(newPrice * 100.0) / 100.0;
                
                updates.add(new PriceUpdate(row, newPrice));
            }
            
            // Apply updates on Swing EDT
            if (!updates.isEmpty()) {
                SwingUtilities.invokeLater(() -> applyUpdates(updates));
            }
            
        } catch (Exception e) {
            logger.error("Error during update: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Apply price updates to the model.
     */
    private void applyUpdates(List<PriceUpdate> updates) {
        updates.stream()
                .filter(update -> !update.row().isLocked())
                .forEach(update -> update.row().setPrice(update.newPrice()));
        
        // Notify table to repaint
        if (tableUpdateCallback != null) {
            tableUpdateCallback.run();
        }
        
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


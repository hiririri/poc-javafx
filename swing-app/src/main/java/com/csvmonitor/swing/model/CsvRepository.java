package com.csvmonitor.swing.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repository class for CSV file operations.
 * 
 * CSV format: id,symbol,price,qty,status,lastUpdate
 */
public class CsvRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(CsvRepository.class);
    private static final String DEFAULT_CSV = "/sample.csv";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int EXPECTED_COLUMNS = 6;
    
    /**
     * Result record for CSV parsing operations.
     */
    public record ParseResult(List<RowData> rows, int errorCount) {
        public boolean hasErrors() {
            return errorCount > 0;
        }
    }
    
    /**
     * Load the built-in sample.csv from resources.
     */
    public List<RowData> loadDefaultCsv() {
        logger.info("Loading default CSV from resources: {}", DEFAULT_CSV);
        try (var is = getClass().getResourceAsStream(DEFAULT_CSV);
             var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return parseCsv(reader).rows();
        } catch (Exception e) {
            logger.error("Failed to load default CSV: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Load CSV from an external file path.
     */
    public List<RowData> loadCsvFromFile(File file) {
        logger.info("Loading CSV from file: {}", file.getAbsolutePath());
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return parseCsv(reader).rows();
        } catch (Exception e) {
            logger.error("Failed to load CSV from file: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Save the current table data to a CSV file.
     */
    public boolean saveCsvToFile(List<RowData> data, File file) {
        logger.info("Saving CSV to file: {}", file.getAbsolutePath());
        try (var writer = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            // Write header
            writer.println("id,symbol,price,qty,status,lastUpdate");
            
            // Write data rows
            data.forEach(row -> writer.println(formatRow(row)));
            
            logger.info("Successfully saved {} rows to CSV", data.size());
            return true;
        } catch (Exception e) {
            logger.error("Failed to save CSV: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Format a single row for CSV output.
     */
    private String formatRow(RowData row) {
        return "%d,%s,%.2f,%d,%s,%s".formatted(
                row.getId(),
                escapeCsv(row.getSymbol()),
                row.getPrice(),
                row.getQty(),
                escapeCsv(row.getStatus()),
                row.getLastUpdate()
        );
    }
    
    /**
     * Parse CSV content from a BufferedReader.
     */
    private ParseResult parseCsv(BufferedReader reader) throws IOException {
        var rows = new ArrayList<RowData>();
        int lineNumber = 0;
        int errorCount = 0;
        
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            
            // Skip header line
            if (lineNumber == 1 && isHeaderLine(line)) {
                logger.debug("Skipping header line: {}", line);
                continue;
            }
            
            // Skip empty lines
            if (line.isBlank()) {
                continue;
            }
            
            // Parse row and handle result
            var result = parseRow(line, lineNumber);
            
            if (result.isPresent()) {
                rows.add(result.get());
            } else {
                errorCount++;
            }
        }
        
        logger.info("Parsed {} rows successfully, {} errors", rows.size(), errorCount);
        return new ParseResult(rows, errorCount);
    }
    
    /**
     * Check if a line is a header line.
     */
    private boolean isHeaderLine(String line) {
        var lower = line.toLowerCase();
        return lower.contains("id") && lower.contains("symbol");
    }
    
    /**
     * Parse a single CSV row.
     */
    private Optional<RowData> parseRow(String line, int lineNumber) {
        try {
            String[] parts = line.split(",", -1);
            
            // Pad array if needed
            if (parts.length < EXPECTED_COLUMNS) {
                logger.warn("Line {} has insufficient columns ({}), padding", lineNumber, parts.length);
                parts = padArray(parts, EXPECTED_COLUMNS);
            }
            
            // Parse fields with safe defaults
            int id = parseNumber(parts[0].trim(), Integer.class).orElse(lineNumber);
            String symbol = parts[1].trim().isEmpty() ? "UNKNOWN" : parts[1].trim();
            double price = parseNumber(parts[2].trim(), Double.class).orElse(0.0);
            int qty = parseNumber(parts[3].trim(), Integer.class).orElse(0);
            String status = normalizeStatus(parts[4].trim());
            String lastUpdate = parts[5].trim().isEmpty() 
                    ? LocalDateTime.now().format(ISO_FORMATTER) 
                    : parts[5].trim();
            
            return Optional.of(new RowData(id, symbol, price, qty, status, lastUpdate));
            
        } catch (Exception e) {
            logger.warn("Error parsing line {}: {} - {}", lineNumber, line, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Normalize status value.
     */
    private String normalizeStatus(String status) {
        if (status.isEmpty()) {
            return "NORMAL";
        }
        
        return switch (status.toUpperCase()) {
            case "ALERT", "WARN", "WARNING" -> "ALERT";
            case "OK", "NORMAL", "GOOD" -> "NORMAL";
            case "PENDING", "WAIT", "WAITING" -> "PENDING";
            case "ACTIVE", "RUNNING", "LIVE" -> "ACTIVE";
            case "CLOSED", "DONE", "COMPLETE", "FINISHED" -> "CLOSED";
            default -> status.toUpperCase();
        };
    }
    
    /**
     * Parse a number safely.
     */
    @SuppressWarnings("unchecked")
    private <T extends Number> Optional<T> parseNumber(String value, Class<T> type) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            Number result = switch (type.getSimpleName()) {
                case "Integer" -> Integer.parseInt(value);
                case "Double" -> Double.parseDouble(value);
                case "Long" -> Long.parseLong(value);
                case "Float" -> Float.parseFloat(value);
                default -> throw new IllegalArgumentException("Unsupported number type: " + type);
            };
            return Optional.of((T) result);
        } catch (NumberFormatException e) {
            logger.debug("Invalid number '{}' for type {}", value, type.getSimpleName());
            return Optional.empty();
        }
    }
    
    /**
     * Pad an array to the required length.
     */
    private String[] padArray(String[] source, int length) {
        var padded = new String[length];
        System.arraycopy(source, 0, padded, 0, source.length);
        for (int i = source.length; i < length; i++) {
            padded[i] = "";
        }
        return padded;
    }
    
    /**
     * Escape a string for CSV output.
     */
    private String escapeCsv(String value) {
        if (value instanceof String s && !s.isEmpty()) {
            if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                return "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }
        return "";
    }
}


package com.csvmonitor.swing.model;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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
    private ParseResult parseCsv(Reader reader) throws IOException {
        var rows = new ArrayList<RowData>();
        int lineNumber = 0;
        int errorCount = 0;

        try (CSVReader csvReader = new CSVReader(reader)) {
            String[] columns;
            while ((columns = csvReader.readNext()) != null) {
                lineNumber++;

                if (lineNumber == 1 && isHeaderLine(columns)) {
                    logger.debug("Skipping header line: {}", Arrays.toString(columns));
                    continue;
                }

                if (isEmptyRow(columns)) {
                    continue;
                }

                var result = parseRow(columns, lineNumber);

                if (result.isPresent()) {
                    rows.add(result.get());
                } else {
                    errorCount++;
                }
            }
        } catch (CsvException e) {
            throw new IOException("Failed to parse CSV", e);
        }

        logger.info("Parsed {} rows successfully, {} errors", rows.size(), errorCount);
        return new ParseResult(rows, errorCount);
    }

    /**
     * Check if a line is a header line.
     */
    private boolean isHeaderLine(String[] columns) {
        if (columns.length < 2) {
            return false;
        }
        return "id".equalsIgnoreCase(columns[0].trim())
                && "symbol".equalsIgnoreCase(columns[1].trim());
    }

    /**
     * Parse a single CSV row.
     */
    private Optional<RowData> parseRow(String[] columns, int lineNumber) {
        try {
            String[] parts = padArray(columns, EXPECTED_COLUMNS);

            // Normalize nulls and trim values
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i] == null ? "" : parts[i].trim();
            }

            int id = parseNumber(parts[0], Integer.class).orElse(lineNumber);
            String symbol = parts[1].isEmpty() ? "UNKNOWN" : parts[1];
            double price = parseNumber(parts[2], Double.class).orElse(0.0);
            int qty = parseNumber(parts[3], Integer.class).orElse(0);
            String status = normalizeStatus(parts[4]);
            String lastUpdate = parts[5].isEmpty()
                    ? LocalDateTime.now().format(ISO_FORMATTER)
                    : parts[5];

            return Optional.of(new RowData(id, symbol, price, qty, status, lastUpdate));

        } catch (Exception e) {
            logger.warn("Error parsing line {}: {} - {}", lineNumber, Arrays.toString(columns), e.getMessage());
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
        int copyLength = Math.min(source.length, length);
        System.arraycopy(source, 0, padded, 0, copyLength);
        for (int i = copyLength; i < length; i++) {
            padded[i] = "";
        }
        return padded;
    }

    /**
     * Determine if the row is effectively empty.
     */
    private boolean isEmptyRow(String[] columns) {
        return Arrays.stream(columns)
                .allMatch(value -> value == null || value.isBlank());
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

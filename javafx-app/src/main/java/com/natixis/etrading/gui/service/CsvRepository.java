package com.natixis.etrading.gui.service;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import com.natixis.etrading.gui.model.RowModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CsvRepository {

    private static final Logger logger = LoggerFactory.getLogger(CsvRepository.class);
    private static final String DEFAULT_CSV = "javafx-app/src/main/resources/sample.csv";

    public ObservableList<RowModel> loadDefaultCsv() {
        try (CSVReader reader = new CSVReader(new FileReader(DEFAULT_CSV))) {
            return reader.readAll().stream()
                    .skip(1)
                    .map(CsvRepository::mapToRowData)
                    .collect(FXCollections::observableArrayList, List::add, List::addAll);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean saveCsvToFile(List<RowModel> data, File file) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(file.getPath()),
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.NO_QUOTE_CHARACTER,  // Remove quotes
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {

            String[] header = {"ID", "Symbol", "Price", "Quantity", "Status", "Last Update"};
            writer.writeNext(header);

            List<String[]> csvRows = data.stream()
                    .map(CsvRepository::mapToStringArray)
                    .toList();

            writer.writeAll(csvRows);
            return true;
        } catch (IOException e) {
            logger.error("Failed to load CSV from file: {}", e.getMessage(), e);
            return false;
        }
    }

    public ObservableList<RowModel> loadCsvFromFile(File file) {
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            return reader.readAll().stream()
                    .skip(1)
                    .map(CsvRepository::mapToRowData)
                    .collect(FXCollections::observableArrayList, List::add, List::addAll);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RowModel mapToRowData(String[] line) {
        return new RowModel(Integer.parseInt(line[0]),
                line[1],
                Double.parseDouble(line[2]),
                Integer.parseInt(line[3]),
                line[4],
                line[5]);
    }

    private static String[] mapToStringArray(RowModel rowData) {
        return new String[]{
                String.valueOf(rowData.getId()),
                rowData.getSymbol(),
                String.valueOf(rowData.getPrice()),
                String.valueOf(rowData.getQty()),
                rowData.getStatus(),
                rowData.getLastUpdate()
        };
    }

}

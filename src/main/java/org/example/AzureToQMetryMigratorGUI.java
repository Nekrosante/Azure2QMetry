package org.example;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.util.*;
import java.util.List;

public class AzureToQMetryMigratorGUI extends JFrame {
    private JButton btnSelectInput;
    private JButton btnMigrate;
    private JButton btnSave;
    private JLabel lblInputFile;
    private JTextArea logArea;
    private File inputFile;
    private Workbook outputWorkbook;

    public AzureToQMetryMigratorGUI() {
        setTitle("Azure to QMetry Migrator");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top panel
        JPanel controlPanel = new JPanel(new GridLayout(3, 2, 5, 5));

        btnSelectInput = new JButton("Select Azure File");
        btnMigrate = new JButton("Migrate to QMetry");
        btnSave = new JButton("Save Result");

        lblInputFile = new JLabel("No file selected");

        btnMigrate.setEnabled(false);
        btnSave.setEnabled(false);

        controlPanel.add(btnSelectInput);
        controlPanel.add(lblInputFile);
        controlPanel.add(btnMigrate);
        controlPanel.add(btnSave);

        add(controlPanel, BorderLayout.NORTH);

        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        // Configure actions
        btnSelectInput.addActionListener(e -> selectInputFile());
        btnMigrate.addActionListener(e -> migrateData());
        btnSave.addActionListener(e -> saveOutputFile());
    }

    private void selectInputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Excel Files", "xlsx"));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            inputFile = fileChooser.getSelectedFile();
            lblInputFile.setText(inputFile.getName());
            log("Azure file selected: " + inputFile.getName());
            btnMigrate.setEnabled(true);
        }
    }

    private void migrateData() {
        try {
            log("Starting migration");

            Workbook azureWorkbook = WorkbookFactory.create(inputFile);
            outputWorkbook = new XSSFWorkbook();
            Sheet resultSheet = outputWorkbook.createSheet("QMetry Test Cases");

            // Headers sin Issue Key
            String[] headers = {
                    "Summary", "Description", "Status", "Priority",
                    "Assignee", "Reporter", "Estimated Time", "Labels", "Components",
                    "Sprint", "Fix Versions", "Step Summary", "Test Data",
                    "Expected Result", "Select List Multiple Choice", "Number Field",
                    "Folders", "Story Linkages"
            };

            Row headerRow = resultSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            Sheet azureSheet = azureWorkbook.getSheetAt(0);
            int resultRowNum = 1;
            int testCaseCount = 0;

            // Variables para el test case actual
            String currentTestCaseTitle = "";
            String currentAreaPath = "";
            List<Row> currentSteps = new ArrayList<>();
            boolean processingTestCase = false;

            for (Row azureRow : azureSheet) {
                if (azureRow.getRowNum() == 0) continue;

                Cell typeCell = safeGetCell(azureRow, 1);
                String type = typeCell != null && typeCell.getCellType() == CellType.STRING ?
                        typeCell.getStringCellValue().trim() : "";

                Cell stepCell = safeGetCell(azureRow, 4);
                String stepAction = stepCell != null && stepCell.getCellType() == CellType.STRING ?
                        stepCell.getStringCellValue().trim() : "";

                // Identificar y omitir nombres de Shared Steps
                if ("Shared Steps".equalsIgnoreCase(type) && !stepAction.isEmpty()) {
                    continue; // Omitir esta fila que es el nombre del Shared Step
                }

                // Nuevo Test Case encontrado
                if ("Test Case".equalsIgnoreCase(type)) {
                    // Guardar el test case anterior si existe
                    if (processingTestCase) {
                        saveTestCase(resultSheet, resultRowNum, currentTestCaseTitle, currentAreaPath, currentSteps);
                        resultRowNum += currentSteps.size() + 1; // +1 por la fila de metadata
                        testCaseCount++;
                    }

                    // Iniciar nuevo Test Case
                    currentTestCaseTitle = safeGetStringCellValue(azureRow, 2);
                    currentAreaPath = safeGetStringCellValue(azureRow, 5);
                    currentSteps = new ArrayList<>();
                    processingTestCase = true;
                    continue; // Saltar la fila del nombre del Test Case
                }

                // Procesar pasos normales (que no son nombres de Shared Steps)
                Cell expectedCell = safeGetCell(azureRow, 5);
                String expectedResult = expectedCell != null && expectedCell.getCellType() == CellType.STRING ?
                        expectedCell.getStringCellValue().trim() : "";

                // Solo incluir filas que tienen Step Action o Expected Result válidos
                if (!stepAction.isEmpty() || !expectedResult.isEmpty()) {
                    currentSteps.add(azureRow);
                }
            }

            // Guardar el último Test Case
            if (processingTestCase) {
                saveTestCase(resultSheet, resultRowNum, currentTestCaseTitle, currentAreaPath, currentSteps);
                testCaseCount++;
            }

            azureWorkbook.close();
            btnSave.setEnabled(true);
            log("Migration completed successfully");
            log("Total test cases migrated: " + testCaseCount);

        } catch (Exception e) {
            log("Migration error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveTestCase(Sheet resultSheet, int startRow, String title, String areaPath, List<Row> steps) {
        // Metadata del Test Case
        Row metadataRow = resultSheet.createRow(startRow);
        safeSetCellValue(metadataRow, 0, title); // Summary
        safeSetCellValue(metadataRow, 2, "TO DO"); // Status
        safeSetCellValue(metadataRow, 8, areaPath); // Components

        // Procesar pasos
        for (int i = 0; i < steps.size(); i++) {
            Row stepRow = resultSheet.createRow(startRow + i + 1);
            Row originalRow = steps.get(i);

            // Step Summary (Step Action)
            Cell stepCell = safeGetCell(originalRow, 4);
            if (stepCell != null && stepCell.getCellType() == CellType.STRING) {
                String stepValue = stepCell.getStringCellValue().trim();
                if (!stepValue.isEmpty()) {
                    safeSetCellValue(stepRow, 11, stepValue);
                }
            }

            // Expected Result
            Cell expectedCell = safeGetCell(originalRow, 5);
            if (expectedCell != null && expectedCell.getCellType() == CellType.STRING) {
                String expectedValue = expectedCell.getStringCellValue().trim();
                if (!expectedValue.isEmpty()) {
                    safeSetCellValue(stepRow, 13, expectedValue);
                }
            }
        }
    }
    // Metodo seguro para obtener valores de celda
    private String safeGetStringCellValue(Row row, int cellNum) {
        try {
            Cell cell = row.getCell(cellNum);
            if (cell == null) return "";

            if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue();
                return value != null ? value.trim() : "";
            } else if (cell.getCellType() == CellType.NUMERIC) {
                return String.valueOf((int)cell.getNumericCellValue());
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }


    private void saveOutputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Excel Files", "xlsx"));
        fileChooser.setSelectedFile(new File("QMetry_Test_Cases.xlsx"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileOutputStream out = new FileOutputStream(fileChooser.getSelectedFile())) {
                outputWorkbook.write(out);
                log("File saved successfully: " + fileChooser.getSelectedFile().getName());
            } catch (IOException e) {
                log("Error saving file: " + e.getMessage());
            }
        }
    }

    private void log(String message) {
        logArea.append(message + "\n");
    }

    private Cell safeGetCell(Row row, int cellNum) {
        try {
            return row.getCell(cellNum);
        } catch (Exception e) {
            return null;
        }
    }


    private void safeSetCellValue(Row row, int cellNum, String value) {
        if (value != null && !value.isEmpty()) {
            row.createCell(cellNum).setCellValue(value);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AzureToQMetryMigratorGUI migrator = new AzureToQMetryMigratorGUI();
            migrator.setVisible(true);
        });
    }
}
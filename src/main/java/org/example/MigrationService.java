package org.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MigrationService {

    private static final String[] HEADERS = {
            "Summary", "Description", "Status", "Priority", "Assignee", "Reporter",
            "Estimated Time", "Labels", "Components", "Sprint", "Fix Versions",
            "Step Summary", "Test Data", "Expected Result", "Select List Multiple Choice",
            "Number Field", "Folders", "Story Linkages"
    };

    private static final int TYPE_COLUMN_INDEX = 1;
    private static final int TITLE_COLUMN_INDEX = 2;
    private static final int STEP_ACTION_COLUMN_INDEX = 4;
    private static final int EXPECTED_RESULT_COLUMN_INDEX = 5;
    private static final int AREA_PATH_COLUMN_INDEX = 5;
    private static final String TEST_CASE_TYPE = "Test Case";
    private static final String SHARED_STEPS_TYPE = "Shared Steps";
    private static final String DEFAULT_STATUS = "TO DO";

    public static MigrationResult migrate(File inputFile) throws IOException {
        Objects.requireNonNull(inputFile, "El archivo de entrada no puede ser nulo");

        try (Workbook azureWorkbook = WorkbookFactory.create(new FileInputStream(inputFile))) {
            Workbook outputWorkbook = new XSSFWorkbook();
            Sheet resultSheet = outputWorkbook.createSheet("QMetry Test Cases");

            createHeaderRow(resultSheet);

            Sheet azureSheet = azureWorkbook.getSheetAt(0);
            MigrationData migrationData = processAzureSheet(azureSheet, resultSheet);

            return new MigrationResult(outputWorkbook, migrationData.testCaseCount);
        }
    }

    private static void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(HEADERS[i]);
        }
    }

    private static MigrationData processAzureSheet(Sheet azureSheet, Sheet resultSheet) {
        MigrationData data = new MigrationData();
        data.resultRowNum = 1;

        for (Row azureRow : azureSheet) {
            if (azureRow.getRowNum() == 0) continue;

            String type = safeGetStringCellValue(azureRow, TYPE_COLUMN_INDEX);
            String stepAction = safeGetStringCellValue(azureRow, STEP_ACTION_COLUMN_INDEX);

            if (isSharedStepToSkip(type, stepAction)) continue;

            if (isNewTestCase(type)) {
                processCurrentTestCase(data, resultSheet);
                initializeNewTestCase(data, azureRow);
                continue;
            }

            processStepRow(data, azureRow);
        }
        processCurrentTestCase(data, resultSheet);

        return data;
    }

    private static boolean isSharedStepToSkip(String type, String stepAction) {
        return SHARED_STEPS_TYPE.equalsIgnoreCase(type) && !stepAction.isEmpty();
    }

    private static boolean isNewTestCase(String type) {
        return TEST_CASE_TYPE.equalsIgnoreCase(type);
    }

    private static void processCurrentTestCase(MigrationData data, Sheet resultSheet) {
        if (data.processingTestCase) {
            saveTestCase(resultSheet, data);
            data.resultRowNum += data.currentSteps.size() + 1;
            data.testCaseCount++;
        }
    }

    private static void initializeNewTestCase(MigrationData data, Row azureRow) {
        data.currentTestCaseTitle = safeGetStringCellValue(azureRow, TITLE_COLUMN_INDEX);
        data.currentAreaPath = safeGetStringCellValue(azureRow, AREA_PATH_COLUMN_INDEX);
        data.currentSteps = new ArrayList<>();
        data.processingTestCase = true;
    }

    private static void processStepRow(MigrationData data, Row azureRow) {
        String expectedResult = safeGetStringCellValue(azureRow, EXPECTED_RESULT_COLUMN_INDEX);
        String stepAction = safeGetStringCellValue(azureRow, STEP_ACTION_COLUMN_INDEX);

        if (!stepAction.isEmpty() || !expectedResult.isEmpty()) {
            data.currentSteps.add(azureRow);
        }
    }

    private static void saveTestCase(Sheet resultSheet, MigrationData data) {
        Row metadataRow = resultSheet.createRow(data.resultRowNum);
        safeSetCellValue(metadataRow, 0, data.currentTestCaseTitle);
        safeSetCellValue(metadataRow, 2, DEFAULT_STATUS);
        safeSetCellValue(metadataRow, 8, data.currentAreaPath);

        for (int i = 0; i < data.currentSteps.size(); i++) {
            Row stepRow = resultSheet.createRow(data.resultRowNum + i + 1);
            Row sourceStep = data.currentSteps.get(i);

            safeSetCellValue(stepRow, 11, safeGetStringCellValue(sourceStep, STEP_ACTION_COLUMN_INDEX));
            safeSetCellValue(stepRow, 13, safeGetStringCellValue(sourceStep, EXPECTED_RESULT_COLUMN_INDEX));
        }
    }

    private static String safeGetStringCellValue(Row row, int cellNum) {
        if (row == null) return "";

        try {
            Cell cell = row.getCell(cellNum, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) return "";

            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private static void safeSetCellValue(Row row, int cellNum, String value) {
        if (row != null && value != null && !value.isEmpty()) {
            row.createCell(cellNum).setCellValue(value);
        }
    }

    private static class MigrationData {
        int resultRowNum;
        int testCaseCount;
        String currentTestCaseTitle;
        String currentAreaPath;
        List<Row> currentSteps;
        boolean processingTestCase;
    }
}

record MigrationResult(Workbook workbook, int testCaseCount) {
    MigrationResult(Workbook workbook, int testCaseCount) {
        this.workbook = Objects.requireNonNull(workbook, "El workbook no puede ser nulo");
        this.testCaseCount = testCaseCount;
    }
}
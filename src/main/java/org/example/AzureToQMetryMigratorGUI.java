package org.example;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import org.apache.poi.ss.usermodel.*;

public class AzureToQMetryMigratorGUI extends JFrame {
    private JButton btnSelectInput, btnMigrate, btnSave;
    private JLabel lblInputFile;
    private JTextArea logArea;
    private File inputFile;
    private Workbook outputWorkbook;
    private int testCaseCount;

    public AzureToQMetryMigratorGUI() {
        setTitle("Azure to QMetry Migrator");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        initUI();
        initActions();
    }

    private void initUI() {
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
        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
    }

    private void initActions() {
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
            MigrationResult result = MigrationService.migrate(inputFile);
            outputWorkbook = result.workbook();
            testCaseCount = result.testCaseCount();
            btnSave.setEnabled(true);
            log("Migration completed successfully");
            log("Total test cases migrated: " + testCaseCount);
        } catch (Exception e) {
            log("Migration error: " + e.getMessage());
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AzureToQMetryMigratorGUI().setVisible(true));
    }
}

package com.imsr;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

@Route("")
public class MainView extends VerticalLayout {

    private final VerticalLayout contentArea = new VerticalLayout();

    private final Tab tabNational = new Tab("NATIONAL ACTIVITY");
    private final Tab tabGacc = new Tab("GACC ACTIVITY");
    private final Tab tabWildfire = new Tab("WILDFIRE ACTIVITY");
    private final Tab tabResource = new Tab("RESOURCE SUMMARY");
    private final Tab tabConsole = new Tab("CONSOLE");

    private final Tabs tabs = new Tabs(tabNational, tabGacc, tabWildfire, tabResource, tabConsole);
    private final TextArea consoleOutput = new TextArea();

    // Replaced TextAreas with dynamic Vaadin Grids
    private final Grid<String[]> nationalGrid = createGrid();
    private final Grid<String[]> gaccGrid = createGrid();
    private final Grid<String[]> wildfireGrid = createGrid();
    private final Grid<String[]> resourceGrid = createGrid();

    // Raw TSV storage for export file generation
    private String nationalTsvData = "";
    private String gaccTsvData = "";
    private String wildfireTsvData = "";
    private String resourceTsvData = "";

    private final String[] header1 = new String[] { "imsr_date", "preparedness_level", "initial_attack_activity",
            "new_fires", "new_large_fires", "contained_large_fires", "uncontained_large_fires", "area_command_teams", "nimos", "type_1_teams", "type_2_teams", "fire_use_teams", "complex_teams" };
    private final String[] header2 = new String[] { "imsr_date", "gacc", "gacc_priority", "preparedness_level", "new_fires",
            "new_large_fires", "uncontained_large_fires", "area_command_teams", "nimos", "type_1_teams", "type_2_teams", "fire_use_teams", "complex_teams" };
    private final String[] header3 = new String[] { "imsr_date", "gacc", "gacc_priority", "fire_priority", "new_large_fire_mark", "fire_name", "unit", "fire_size",
            "fire_size_change", "percent_containment", "contained_completed", "estimated_containment_date", "personnel", "personnel_change", "crews",
            "engines", "helicopters", "structures_lost", "cost_to_date", "origin_ownership" };
    private final String[] header4 = new String[] { "imsr_date", "gacc", "incidents", "cumulative_size", "crews", "engines", "helicopters", "personnel", "personnel_change", "preparedness_level" };

    private final List<File> uploadedBatchFiles = new ArrayList<>();
    private File currentBatchDir = null;

    public MainView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 title = new H2("IMSR Webapp");

        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".pdf");
        upload.setMaxFileSize(50 * 1024 * 1024); // 50MB limit per file
        upload.setMaxFiles(100);

        Button uploadButton = new Button("SELECT IMSR PDFs", new Icon(VaadinIcon.UPLOAD));
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        upload.setUploadButton(uploadButton);

        upload.addSucceededListener(event -> {
            String fileName = event.getFileName();
            try {
                InputStream inputStream = buffer.getInputStream(fileName);
                if (inputStream != null) {
                    synchronized (this) {
                        if (currentBatchDir == null) {
                            String sessionFolderName = "imsr_batch_" + UUID.randomUUID().toString();
                            currentBatchDir = new File(System.getProperty("java.io.tmpdir"), sessionFolderName);
                            if (!currentBatchDir.exists()) {
                                currentBatchDir.mkdirs();
                            }
                        }
                    }

                    File targetFile = new File(currentBatchDir, fileName);
                    try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                        byte[] bufferBytes = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(bufferBytes)) != -1) {
                            outputStream.write(bufferBytes, 0, bytesRead);
                        }
                    }

                    synchronized (uploadedBatchFiles) {
                        uploadedBatchFiles.add(targetFile);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        upload.addAllFinishedListener(event -> {
            File[] fileArray;
            synchronized (uploadedBatchFiles) {
                fileArray = uploadedBatchFiles.toArray(new File[0]);
                uploadedBatchFiles.clear();
            }
            
            synchronized (this) {
                currentBatchDir = null;
            }

            if (fileArray.length > 0) {
                runAggregationOnFiles(fileArray);
            }
        });

        HorizontalLayout topBar = new HorizontalLayout(title, upload);
        topBar.setAlignItems(Alignment.CENTER);
        topBar.setWidthFull();
        topBar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        add(topBar);

        tabs.setWidthFull();
        tabs.addSelectedChangeListener(event -> updateContent(event.getSelectedTab()));

        contentArea.setSizeFull();
        contentArea.setPadding(false);
        add(tabs, contentArea);

        consoleOutput.setSizeFull();
        consoleOutput.setReadOnly(true);
        consoleOutput.setValue("System ready, select or drop IMSR PDFs using the upload component above.\n");

        tabs.setSelectedTab(tabConsole);
        updateContent(tabConsole);
    }

    private Grid<String[]> createGrid() {
        Grid<String[]> grid = new Grid<>();
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);
        return grid;
    }

    private void configureGridColumns(Grid<String[]> grid, String[] headers) {
        grid.removeAllColumns();
        for (int i = 0; i < headers.length; i++) {
            final int colIndex = i;
            grid.addColumn(row -> colIndex < row.length ? row[colIndex] : "")
                .setHeader(headers[colIndex])
                .setSortable(true)
                .setAutoWidth(true)
                .setResizable(true);
        }
    }

    private VerticalLayout buildDataView(String titleText, Grid<String[]> grid, String headerTitle, String[] headers, String rawTsvData) {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(true);

        H2 header = new H2(titleText);

        // Export TSV Button
        Button downloadTsvBtn = new Button("Export TSV", new Icon(VaadinIcon.DOWNLOAD));
        downloadTsvBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        StreamResource tsvResource = new StreamResource(headerTitle.toLowerCase().replace(" ", "_") + ".tsv",
                () -> new ByteArrayInputStream(rawTsvData.getBytes(StandardCharsets.UTF_8)));

        Anchor downloadTsvAnchor = new Anchor(tsvResource, "");
        downloadTsvAnchor.setTarget("_blank");
        downloadTsvAnchor.add(downloadTsvBtn);

        // Export CSV Button
        Button downloadCsvBtn = new Button("Export CSV", new Icon(VaadinIcon.FILE_TABLE));
        downloadCsvBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        String csvData = rawTsvData.replace("\t", ",");
        StreamResource csvResource = new StreamResource(headerTitle.toLowerCase().replace(" ", "_") + ".csv",
                () -> new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8)));

        Anchor downloadCsvAnchor = new Anchor(csvResource, "");
        downloadCsvAnchor.setTarget("_blank");
        downloadCsvAnchor.add(downloadCsvBtn);

        HorizontalLayout toolbar = new HorizontalLayout(header, new HorizontalLayout(downloadTsvAnchor, downloadCsvAnchor));
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        layout.add(toolbar, grid);
        layout.setFlexGrow(1, grid);
        return layout;
    }

    private void runAggregationOnFiles(File[] pdfFiles) {
        pdfFiles = prepareAndCleanFiles(pdfFiles);

        consoleOutput.setValue("");

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream ps = new java.io.PrintStream(baos);
        java.io.PrintStream oldOut = System.out;
        java.io.PrintStream oldErr = System.err;
        System.setOut(ps);
        System.setErr(ps);

        StringBuilder logBuilder = new StringBuilder();

        File rawFolder = null;
        File simple2Folder = null;
        File baseDir = null;

        try {
            if (pdfFiles != null && pdfFiles.length > 0) {
                baseDir = pdfFiles[0].getParentFile();
                rawFolder = new File(baseDir, "raw");
                simple2Folder = new File(baseDir, "simple2");

                if (!rawFolder.exists()) {
                    rawFolder.mkdirs();
                }
                if (!simple2Folder.exists()) {
                    simple2Folder.mkdirs();
                }

                Utility.convert_pdf_to_text_files(pdfFiles, "both");

                if (rawFolder.exists() && simple2Folder.exists()) {
                    List<IMSR_Process> processedList = new ArrayList<>();

                    for (File pdfFile : pdfFiles) {
                        String pdfName = pdfFile.getName();
                        String txtName = pdfName.substring(0, pdfName.lastIndexOf('.')) + ".txt";

                        File rawFile = new File(rawFolder, txtName);
                        File simple2File = new File(simple2Folder, txtName);

                        if (rawFile.exists() && simple2File.exists()) {
                            processedList.add(new IMSR_Process(simple2File, rawFile));
                            rawFile.delete();
                            simple2File.delete();
                        }
                    }
                    logBuilder.append("Number of processed PDF files: ").append(pdfFiles.length).append("\n");

                    if (!processedList.isEmpty()) {
                        IMSR_Process[] processedArray = processedList.toArray(new IMSR_Process[0]);

                        // --- NATIONAL ACTIVITY ---
                        configureGridColumns(nationalGrid, header1);
                        List<String[]> nationalRows = new ArrayList<>();
                        StringBuilder nationalContent = new StringBuilder();
                        nationalContent.append(String.join("\t", header1)).append("\n");

                        for (IMSR_Process ismr : processedArray) {
                        	nationalRows.add(ismr.national_activity.toArray(new String[0]));
                            nationalContent.append(String.join("\t", ismr.national_activity)).append("\n");
                        }
                        nationalGrid.setItems(nationalRows);
                        nationalTsvData = nationalContent.toString();

                        // --- GACC ACTIVITY ---
                        configureGridColumns(gaccGrid, header2);
                        List<String[]> gaccRows = new ArrayList<>();
                        StringBuilder gaccContent = new StringBuilder();
                        gaccContent.append(String.join("\t", header2)).append("\n");

                        for (IMSR_Process ismr : processedArray) {
                            for (String st : ismr.gacc_activity) {
                                String[] row = st.split("\t");
                                gaccRows.add(row);
                                gaccContent.append(st).append("\n");
                            }
                        }
                        gaccGrid.setItems(gaccRows);
                        gaccTsvData = gaccContent.toString();

                        // --- RESOURCE SUMMARY ---
                        configureGridColumns(resourceGrid, header4);
                        List<String[]> resourceRows = new ArrayList<>();
                        StringBuilder resourceContent = new StringBuilder();
                        resourceContent.append(String.join("\t", header4)).append("\n");

                        for (IMSR_Process ismr : processedArray) {
                            for (String st : ismr.resource_summary) {
                                String[] row = st.split("\t");
                                resourceRows.add(row);
                                resourceContent.append(st).append("\n");
                            }
                        }
                        resourceGrid.setItems(resourceRows);
                        resourceTsvData = resourceContent.toString();

                        // --- WILDFIRE ACTIVITY ---
                        configureGridColumns(wildfireGrid, header3);
                        List<String> final_fires = new ArrayList<>();
                        for (IMSR_Process ismr : processedArray) {
                            final_fires.addAll(ismr.final_fires);
                        }
                        fix_ctd(final_fires);

                        List<String[]> wildfireRows = new ArrayList<>();
                        StringBuilder wildfireContent = new StringBuilder();
                        wildfireContent.append(String.join("\t", header3)).append("\n");

                        for (String fire : final_fires) {
                            String[] row = fire.split("\t");
                            wildfireRows.add(row);
                            wildfireContent.append(fire).append("\n");
                        }
                        wildfireGrid.setItems(wildfireRows);
                        wildfireTsvData = wildfireContent.toString();

                    } else {
                        System.out.println("ERROR: No matching text files found for selection.");
                    }
                } else {
                    System.out.println("ERROR: 'raw' or 'simple2' output subfolders do not exist.");
                }
            } else {
                System.out.println("ERROR: No PDF files selected.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (rawFolder != null && rawFolder.exists()) {
                    File[] files = rawFolder.listFiles();
                    if (files != null) {
                        for (File f : files) f.delete();
                    }
                    rawFolder.delete();
                }
                if (simple2Folder != null && simple2Folder.exists()) {
                    File[] files = simple2Folder.listFiles();
                    if (files != null) {
                        for (File f : files) f.delete();
                    }
                    simple2Folder.delete();
                }
                if (baseDir != null && baseDir.exists()) {
                    File[] files = baseDir.listFiles();
                    if (files != null) {
                        for (File f : files) f.delete();
                    }
                    baseDir.delete();
                }
            } catch (Exception e) {
                System.out.println("Warning: Could not fully clean up temporary workspace folders.");
            }

            System.setOut(oldOut);
            System.setErr(oldErr);
        }

        String capturedLogs = baos.toString();
        if (!capturedLogs.isEmpty()) {
            logBuilder.append(capturedLogs);
        }

        logBuilder.append("----------------------------------------------------------------------------------------------------------------------------\n");
        logBuilder.append("AGGREGATION IS COMPLETED - ALL RESULTS ARE READY FOR EXPORTATION\n");
        logBuilder.append("----------------------------------------------------------------------------------------------------------------------------\n");

        consoleOutput.setValue(logBuilder.toString());
        tabs.setSelectedTab(tabConsole);
        updateContent(tabConsole);
    }

    private File[] prepareAndCleanFiles(File[] pdfFiles) {
        if (pdfFiles == null || pdfFiles.length == 0) return new File[0];

        Map<String, File> sortedUniqueFiles = new TreeMap<>();

        for (File file : pdfFiles) {
            try {
                String fileName = file.getName();
                String dateKey = extractDateString(fileName).replace("-", ""); // YYYYMMDD
                String newFileName = dateKey + "IMSR.pdf";

                File targetFile = new File(file.getParentFile(), newFileName);

                if (!targetFile.exists()) {
                    try {
                        Files.move(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        sortedUniqueFiles.put(dateKey, targetFile);
                    } catch (Exception ex) {
                        sortedUniqueFiles.putIfAbsent(dateKey, file);
                    }
                } else {
                    if (!file.getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                        file.delete();
                    }
                    sortedUniqueFiles.putIfAbsent(dateKey, targetFile);
                }
            } catch (Exception e) {
                System.out.println("Could not parse date for renaming: " + file.getName());
            }
        }

        return sortedUniqueFiles.values().toArray(new File[0]);
    }

    private String extractDateString(String fileName) {
        java.util.regex.Pattern alreadyCleanPattern = java.util.regex.Pattern.compile("^(\\d{8})IMSR");
        java.util.regex.Matcher cleanMatcher = alreadyCleanPattern.matcher(fileName);
        if (cleanMatcher.find()) {
            return cleanMatcher.group(1);
        }

        String parseName = fileName;
        if (parseName.contains("IMSR")) {
            parseName = parseName.substring(parseName.indexOf("IMSR"));
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{8})");
        java.util.regex.Matcher matcher = pattern.matcher(parseName);

        if (matcher.find()) {
            String mmddyyyy = matcher.group(1);
            String mm = mmddyyyy.substring(0, 2);
            String dd = mmddyyyy.substring(2, 4);
            String yyyy = mmddyyyy.substring(4, 8);
            return yyyy + mm + dd;
        }

        return fileName.replaceAll("(?i)\\.pdf$", "");
    }

    private void fix_ctd(List<String> final_fires) {
        for (int i = 0; i < final_fires.size(); i++) {
            String[] fs = final_fires.get(i).split("\t");
            if (fs.length <= 19) continue;

            if (fs[18].endsWith("KI")) {
                fs[18] = fs[18].substring(0, fs[18].length() - 1);
                final_fires.set(i, String.join("\t", fs));
            }
            if (fs[18].equals("7/18")) {
                fs[18] = "NR";
                final_fires.set(i, String.join("\t", fs));
            }
            if (fs[19].equals("3.8M")) {
                fs[19] = "FS";
                final_fires.set(i, String.join("\t", fs));
            }

            try {
                if (!(fs[18].equals("NA") || fs[18].equals("NR") || fs[18].equals("---") || fs[18].endsWith("K") || fs[18].endsWith("M"))) {
                    boolean continue_loop = true;
                    int l = i;
                    do {
                        l = l - 1;
                        if (l < 0) break;
                        String[] previous_fs = final_fires.get(l).split("\t");
                        if (previous_fs.length > 18 && previous_fs[5].equals(fs[5]) && (previous_fs[18].endsWith("K") || previous_fs[18].endsWith("M"))) {
                            double previous_ctd = Double.parseDouble(previous_fs[18].substring(0, previous_fs[18].length() - 1));
                            double ctd = Double.parseDouble(fs[18]);
                            if (previous_ctd <= ctd) {
                                fs[18] = fs[18] + previous_fs[18].substring(previous_fs[18].length() - 1);
                            } else {
                                fs[18] = fs[18] + "M";
                            }
                            final_fires.set(i, String.join("\t", fs));
                            continue_loop = false;
                        }
                    } while (continue_loop && l > 0);
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        for (int i = final_fires.size() - 1; i >= 0; i--) {
            String[] fs = final_fires.get(i).split("\t");
            if (fs.length <= 19) continue;

            try {
                if (!(fs[18].equals("NA") || fs[18].equals("NR") || fs[18].equals("---") || fs[18].endsWith("K") || fs[18].endsWith("M"))) {
                    boolean continue_loop = true;
                    int l = i;
                    do {
                        l = l + 1;
                        if (l >= final_fires.size()) break;
                        String[] next_fs = final_fires.get(l).split("\t");
                        if (next_fs.length > 18 && next_fs[5].equals(fs[5]) && (next_fs[18].endsWith("K") || next_fs[18].endsWith("M"))) {
                            double next_ctd = Double.parseDouble(next_fs[18].substring(0, next_fs[18].length() - 1));
                            double ctd = Double.parseDouble(fs[18]);
                            if (next_ctd >= ctd) {
                                fs[18] = fs[18] + next_fs[18].substring(next_fs[18].length() - 1);
                            } else {
                                fs[18] = fs[18] + "K";
                            }
                            final_fires.set(i, String.join("\t", fs));
                            continue_loop = false;
                        }
                    } while (continue_loop && l < final_fires.size() - 1);
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateContent(Tab selectedTab) {
        contentArea.removeAll();

        if (selectedTab.equals(tabNational)) {
            contentArea.add(buildDataView("National Activity Data", nationalGrid, "national_activity", header1, nationalTsvData));
        } else if (selectedTab.equals(tabGacc)) {
            contentArea.add(buildDataView("GACC Activity Data", gaccGrid, "gacc_activity", header2, gaccTsvData));
        } else if (selectedTab.equals(tabWildfire)) {
            contentArea.add(buildDataView("Wildfire Activity Data", wildfireGrid, "wildfire_activity", header3, wildfireTsvData));
        } else if (selectedTab.equals(tabResource)) {
            contentArea.add(buildDataView("Resource Summary Data", resourceGrid, "resource_summary", header4, resourceTsvData));
        } else if (selectedTab.equals(tabConsole)) {
            contentArea.add(consoleOutput);
        }
    }
}
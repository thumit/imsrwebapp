package com.imsr;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;

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

    // Data text areas for your 4 output categories
    private final TextArea nationalDataArea = createDataTextArea();
    private final TextArea gaccDataArea = createDataTextArea();
    private final TextArea wildfireDataArea = createDataTextArea();
    private final TextArea resourceDataArea = createDataTextArea();

    // Headers corresponding to your extraction logic
    private final String[] header1 = new String[] { "imsr_date", "preparedness_level", "initial_attack_activity",
            "new_fires", "new_large_fires", "contained_large_fires", "uncontained_large_fires", "area_command_teams", "nimos", "type_1_teams", "type_2_teams", "fire_use_teams", "complex_teams" };
    private final String[] header2 = new String[] { "imsr_date", "gacc", "gacc_priority", "preparedness_level", "new_fires",
            "new_large_fires", "uncontained_large_fires", "area_command_teams", "nimos", "type_1_teams", "type_2_teams", "fire_use_teams", "complex_teams" };
    private final String[] header3 = new String[] { "imsr_date", "gacc", "gacc_priority", "fire_priority", "new_large_fire_mark", "fire_name", "unit", "fire_size",
            "fire_size_change", "percent_containment", "contained_completed", "estimated_containment_date", "personnel", "personnel_change", "crews",
            "engines", "helicopters", "structures_lost", "cost_to_date", "origin_ownership" };
    private final String[] header4 = new String[] { "imsr_date", "gacc", "incidents", "cumulative_size", "crews", "engines", "helicopters", "personnel", "personnel_change", "preparedness_level" };

    public MainView() {
//        setSizeFull();
//        setPadding(true);
//        setSpacing(true);
//        setFlexGrow(1, contentArea);
//        setMinHeight("0px");
//        setMaxHeight("300px");

        H2 title = new H2("IMSR Webapp");
        
        // Hook button directly to the Swing File Chooser trigger
        Button processButton = new Button("SELECT IMSR PDFs", e -> openFileChooser());
        processButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        HorizontalLayout topBar = new HorizontalLayout(title, processButton);
        topBar.setAlignItems(Alignment.CENTER);
        topBar.setWidthFull();
        topBar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        add(topBar);

        tabs.setWidthFull();
        tabs.addSelectedChangeListener(event -> updateContent(event.getSelectedTab()));

        contentArea.setSizeFull();
        add(tabs, contentArea);

        consoleOutput.setSizeFull();
        consoleOutput.setReadOnly(true);
        consoleOutput.setValue("System ready, click 'SELECT IMSR PDFs' \n");
        
        tabs.setSelectedTab(tabConsole);
        updateContent(tabConsole);
    }

    private TextArea createDataTextArea() {
        TextArea area = new TextArea();
        area.setSizeFull();
        setFlexGrow(1, area);
        area.setReadOnly(true);
        area.setValue("No results. Click 'SELECT IMSR PDFs' to process files.");
        return area;
    }

    private void runAggregationOnFiles(File[] pdfFiles) {
        // --- CLEAR PREVIOUS UI DATA AREAS ---
        nationalDataArea.setValue("");
        gaccDataArea.setValue("");
        wildfireDataArea.setValue("");
        resourceDataArea.setValue("");
        consoleOutput.setValue("");

        // Capture standard output and error to grab live prints during processing
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream ps = new java.io.PrintStream(baos);
        java.io.PrintStream oldOut = System.out;
        java.io.PrintStream oldErr = System.err;
        System.setOut(ps);
        System.setErr(ps);

        StringBuilder logBuilder = new StringBuilder();
        
     // Keep track of folders so we can clean them up securely
        File rawFolder = null;
        File simple2Folder = null;

        try {
            if (pdfFiles != null && pdfFiles.length > 0) {
                
                // Determine base directory dynamically from selected files or fallback
                File baseDir = pdfFiles[0].getParentFile();
                rawFolder = new File(baseDir, "raw");
                simple2Folder = new File(baseDir, "simple2");

                if (!baseDir.exists() || (!new File(baseDir, "raw").exists())) {
                    baseDir = new File("C:\\atest");
                    rawFolder = new File(baseDir, "raw");
                    simple2Folder = new File(baseDir, "simple2");
                }

                // Convert the newly selected PDFs (this overwrites matching names automatically)
                Utility.convert_pdf_to_text_files(pdfFiles, "both");

                if (rawFolder.exists() && simple2Folder.exists()) {
                    List<IMSR_Process> processedList = new ArrayList<>();

                    // Loop ONLY through the specific PDFs you just selected
                    for (File pdfFile : pdfFiles) {
                        String pdfName = pdfFile.getName();
                        // Change extension from .pdf to .txt to find the matching text file
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

                        // --- BUILD NATIONAL ACTIVITY ---
                        StringBuilder nationalContent = new StringBuilder();
                        nationalContent.append(String.join("\t", header1)).append("\n");
                        for (IMSR_Process ismr : processedArray) {
                            nationalContent.append(String.join("\t", ismr.national_activity)).append("\n");
                        }
                        nationalDataArea.setValue(nationalContent.toString());

                        // --- BUILD GACC ACTIVITY ---
                        StringBuilder gaccContent = new StringBuilder();
                        gaccContent.append(String.join("\t", header2)).append("\n");
                        for (IMSR_Process ismr : processedArray) {
                            for (String st : ismr.gacc_activity) {
                                gaccContent.append(st).append("\n");
                            }
                        }
                        gaccDataArea.setValue(gaccContent.toString());

                        // --- BUILD RESOURCE SUMMARY ---
                        StringBuilder resourceContent = new StringBuilder();
                        resourceContent.append(String.join("\t", header4)).append("\n");
                        for (IMSR_Process ismr : processedArray) {
                            for (String st : ismr.resource_summary) {
                                resourceContent.append(st).append("\n");
                            }
                        }
                        resourceDataArea.setValue(resourceContent.toString());

                        // --- BUILD WILDFIRE ACTIVITY (with fix_ctd) ---
                        List<String> final_fires = new ArrayList<>();
                        for (IMSR_Process ismr : processedArray) {
                            final_fires.addAll(ismr.final_fires);
                        }
                        fix_ctd(final_fires);

                        StringBuilder wildfireContent = new StringBuilder();
                        wildfireContent.append(String.join("\t", header3)).append("\n");
                        for (String fire : final_fires) {
                            wildfireContent.append(fire).append("\n");
                        }
                        wildfireDataArea.setValue(wildfireContent.toString());

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
        	// CLEANUP: Delete text files and folders immediately when done
            try {
                if (rawFolder != null && rawFolder.exists()) {
                    File[] files = rawFolder.listFiles();
                    if (files != null) {
                        for (File f : files) f.delete();
                    }
                    rawFolder.delete(); // Delete the folder itself
                }
                if (simple2Folder != null && simple2Folder.exists()) {
                    File[] files = simple2Folder.listFiles();
                    if (files != null) {
                        for (File f : files) f.delete();
                    }
                    simple2Folder.delete(); // Delete the folder itself
                }
            } catch (Exception e) {
                System.out.println("Warning: Could not fully clean up temporary text folders.");
            }
            
            // Restore original system streams
            System.setOut(oldOut);
            System.setErr(oldErr);
        }

        // Append captured logs and print completion footer
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

    private void fix_ctd(List<String> final_fires) {
        String raw_number_record_list = "";
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
            contentArea.add(new H2("National Activity Data"), nationalDataArea);
        } else if (selectedTab.equals(tabGacc)) {
            contentArea.add(new H2("GACC Activity Data"), gaccDataArea);
        } else if (selectedTab.equals(tabWildfire)) {
            contentArea.add(new H2("Wildfire Activity Data"), wildfireDataArea);
        } else if (selectedTab.equals(tabResource)) {
            contentArea.add(new H2("Resource Summary Data"), resourceDataArea);
        } else if (selectedTab.equals(tabConsole)) {
            contentArea.add(consoleOutput);
        }
    }
    
    private void openFileChooser() {
        // Capture the current Vaadin UI instance safely from the request thread
        com.vaadin.flow.component.UI currentUi = com.vaadin.flow.component.UI.getCurrent();

        // Ensure Swing runs safely on a thread
        SwingUtilities.invokeLater(() -> {
            try {
                // Set look and feel to native OS style
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            // Create an invisible, always-on-top frame to force the dialog to the front
            javax.swing.JFrame dummyFrame = new javax.swing.JFrame();
            dummyFrame.setAlwaysOnTop(true);
            dummyFrame.setVisible(false);

            JFileChooser fileChooser = new JFileChooser("C:\\");
            fileChooser.setDialogTitle("Select IMSR PDF Reports");
            fileChooser.setMultiSelectionEnabled(true);
            fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Files (*.pdf)", "pdf"));

            int userSelection = fileChooser.showOpenDialog(dummyFrame);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File[] selectedFiles = fileChooser.getSelectedFiles();
                if (selectedFiles.length > 0 && currentUi != null) {
                    // Use ui.access to safely acquire the Vaadin session lock from the Swing thread
                    currentUi.access(() -> {
                        runAggregationOnFiles(selectedFiles);
                    });
                }
            }
            
            // Clean up the dummy frame
            dummyFrame.dispose();
        });
    }
}
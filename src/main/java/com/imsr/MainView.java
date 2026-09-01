package com.imsr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

@Route("")
public class MainView extends VerticalLayout {

    private final VerticalLayout contentArea = new VerticalLayout();

    private Dialog loadingDialog;
    private final Tab tabAbout = createTab("ABOUT US", VaadinIcon.INFO_CIRCLE);
    private final Tab tabLicense = createTab("LICENSE", VaadinIcon.DIPLOMA);
    private final Tab tabArchive = createTab("ARCHIVE", VaadinIcon.DIPLOMA);
    private final Tab tabConsole = createTab("CONSOLE", VaadinIcon.TERMINAL);
    private final Tab tabNational = createTab("NATIONAL ACTIVITY", VaadinIcon.GLOBE);
    private final Tab tabGacc = createTab("GACC ACTIVITY", VaadinIcon.MAP_MARKER);
    private final Tab tabWildfire = createTab("WILDFIRE ACTIVITY", VaadinIcon.FIRE);
    private final Tab tabResource = createTab("RESOURCE SUMMARY", VaadinIcon.USERS);

    private final Tabs tabs = new Tabs(tabAbout, tabLicense, tabArchive, tabConsole, tabNational, tabGacc, tabWildfire, tabResource );
    private final TextArea consoleOutput = new TextArea();

    // Dynamic Vaadin Grids
    private final Grid<String[]> nationalGrid = createGrid();
    private final Grid<String[]> gaccGrid = createGrid();
    private final Grid<String[]> wildfireGrid = createGrid();
    private final Grid<String[]> resourceGrid = createGrid();

    // Raw data storage for exports
    private String nationalTsvData = "";
    private String gaccTsvData = "";
    private String wildfireTsvData = "";
    private String resourceTsvData = "";
    private byte[] cachedExcelBytes;

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
        setPadding(false);
        setSpacing(false);
        getStyle().set("background-color", "#f8fafc");

        // Top Navigation Bar
        HorizontalLayout topBar = createTopHeaderBar();
        
        // Navigation Tabs Styling
        tabs.setWidthFull();
        tabs.addThemeVariants(TabsVariant.LUMO_CENTERED, TabsVariant.LUMO_EQUAL_WIDTH_TABS);
        tabs.getStyle()
                .set("background-color", "#ffffff")
                .set("box-shadow", "0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1)")
                .set("border-bottom", "1px solid #e2e8f0");
        tabs.addSelectedChangeListener(event -> updateContent(event.getSelectedTab()));

        // Main Content Area Container
        contentArea.setSizeFull();
        contentArea.setPadding(true);
        contentArea.getStyle()
		        .set("display", "flex")
		        .set("flex-direction", "column");

        add(topBar, tabs, contentArea);
        setFlexGrow(1, contentArea);

        // Terminal Console Setup
        consoleOutput.setSizeFull();
        consoleOutput.setReadOnly(true);
        consoleOutput.getStyle()
                .set("font-family", "'Fira Code', 'Courier New', monospace")
                .set("font-size", "0.875rem")
                .set("background-color", "#ffffff")
                .set("color", "#0f172a")
                .set("border-radius", "0.75rem")
                .set("border", "1px solid #cbd5e1")
                .set("box-shadow", "0 4px 6px -1px rgba(0,0,0,0.05)");
        tabs.setSelectedTab(tabConsole);
        updateContent(tabConsole);
        
        File todayFile = fetchAndRenameTodayIMSR();
        if (todayFile != null && todayFile.exists()) {
            // Run extraction immediately on launch to show today result
            runAggregationOnFiles(new File[]{todayFile});
            consoleOutput.setValue("> If you use data generated by this webapp, please cite our paper:"
            		+ " Nguyen, D., Belval, E.J., Wei, Y. et al. Dataset of United States Incident Management Situation Reports from 2007 to 2021. Sci Data 11, 23 (2024). https://doi.org/10.1038/s41597-023-02876-8\n"
            		+ "> Today (or the latest) IMSR data extraction can be found in the next 4 tabs.\n"
            		+ "> Historical IMSR PDFs can be downloaded at https://www.nifc.gov/nicc/incident-information/imsr.\n"
            		+ "> Select (no more than 100) PDFs for extraction using the orange button above.\n");

        }
    }

    private void createLoadingDialog() {
        loadingDialog = new Dialog();
        loadingDialog.setCloseOnEsc(false);
        loadingDialog.setCloseOnOutsideClick(false);

        ProgressBar progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);

        Span statusLabel = new Span("Processing IMSR PDFs... Please wait.");
        statusLabel.getStyle()
                .set("font-weight", "600")
                .set("color", "#0f172a");

        VerticalLayout dialogLayout = new VerticalLayout(progressBar, statusLabel);
        dialogLayout.setAlignItems(Alignment.CENTER);
        dialogLayout.setPadding(true);
        dialogLayout.setSpacing(true);

        loadingDialog.add(dialogLayout);
    }

    private void showLoading(boolean show) {
        if (loadingDialog == null) {
            createLoadingDialog();
        }
        if (show) {
            loadingDialog.open();
        } else {
            loadingDialog.close();
        }
    }
    
    private Tab createTab(String text, VaadinIcon iconType) {
        Icon icon = iconType.create();
        icon.getStyle()
                .set("margin-right", "0.5rem")
                .set("width", "1.1rem")
                .set("height", "1.1rem");
        Span titleSpan = new Span(text);
        HorizontalLayout tabLayout = new HorizontalLayout(icon, titleSpan);
        tabLayout.setAlignItems(Alignment.CENTER);
        return new Tab(tabLayout);
    }

    private HorizontalLayout createTopHeaderBar() {
        // App Title & Badge
        Icon logoIcon = VaadinIcon.FIRE.create();
        logoIcon.getStyle()
                .set("color", "#ea580c")
                .set("width", "2rem")
                .set("height", "2rem");

        H2 title = new H2("IMSR Extraction Platform");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "1.25rem")
                .set("font-weight", "700")
                .set("color", "#ffffff");

        Span badge = new Span("Pro");
        badge.getStyle()
                .set("background-color", "#0284c7")
                .set("color", "#ffffff")
                .set("font-size", "0.65rem")
                .set("font-weight", "800")
                .set("padding", "0.15rem 0.5rem")
                .set("border-radius", "9999px")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em");

        HorizontalLayout brandLayout = new HorizontalLayout(logoIcon, title, badge);
        brandLayout.setAlignItems(Alignment.CENTER);
        brandLayout.setSpacing(true);
        brandLayout.getStyle().set("flex-shrink", "0");

        // Operational Transition Banner (Fills center gap) 
        Div operationalNotice = new Div();
        operationalNotice.getStyle()
                .set("background-color", "rgba(255, 255, 255, 0.05)")
                .set("border-left", "4px solid #0284c7")
                .set("border-radius", "0.375rem")
                .set("padding", "0.5rem 1rem")
                .set("margin", "0 1.5rem")
                .set("color", "#f8fafc")
                .set("font-size", "0.8rem")
                .set("line-height", "1.4")
                .set("min-width", "280px")
                .set("max-width", "680px")
                .set("flex-shrink", "1");      // Allows mild squishing down width;

        operationalNotice.add(new Html("<div>"
                + "<strong style=\"color: #38bdf8; text-transform: uppercase; letter-spacing: 0.05em; font-size: 0.70rem; display: block; margin-bottom: 0.15rem;\">"
                + "System Announcement"
                + "</strong>"
                + "In June 2026, the National Interagency Coordination Center (NICC) Predictive Services initiated a strategic discussion to "
                + "<em>\"move the IMSR scraping and data stewardship out of the research realm and into operations\"</em>, "
                + "as this work <em>\"fulfilled a need so effectively that we want it known that this work is endorsed by the business and will be sustained indefinitely. "
                + "And, if we can make the data available in real time to the research and business communities, we'd support that too.\"</em>"
                + "&nbsp; In response, we developed this web application to deliver real-time IMSR data extraction to wildland fire communities and the wider public."
                + "</div>"));

        // Upload Component Setup
        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".pdf");
        upload.setMaxFileSize(50 * 1024 * 1024);
        upload.setMaxFiles(100);

        Button uploadButton = new Button("SELECT IMSR PDFs", VaadinIcon.CLOUD_UPLOAD.create());
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        uploadButton.getStyle()
                .set("background-color", "#ea580c")
                .set("color", "#ffffff")
                .set("font-weight", "600")
                .set("border-radius", "0.5rem")
                .set("cursor", "pointer");
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

        upload.addStartedListener(event -> {
            showLoading(true);
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
            showLoading(false);
        });

        // Fixed scroll wrapper
        Div scrollWrapper = new Div(upload);
        scrollWrapper.getStyle()
                .set("max-height", "180px")
                .set("overflow-y", "auto")
                .set("overflow-x", "hidden")   // Hide the horizontal scrollbar
                .set("display", "flex")
                .set("align-items", "flex-start")
		        .set("flex-shrink", "0")       // Prevents the center banner from squishing this container
		        .set("min-width", "180px");    // Guarantees enough room for the button and drop zone

        HorizontalLayout topBar = new HorizontalLayout(brandLayout, operationalNotice, scrollWrapper);
        topBar.setWidthFull();
        topBar.setAlignItems(Alignment.CENTER);
        topBar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        topBar.getStyle().set("flex-wrap", "wrap");
        topBar.setFlexGrow(1, operationalNotice);
        topBar.getStyle()
                .set("background", "linear-gradient(135deg, #0f172a 0%, #1e293b 100%)")
                .set("padding", "0.75rem 2rem")
                .set("box-shadow", "0 4px 6px -1px rgba(0, 0, 0, 0.1)");

        return topBar;
    }

    private Grid<String[]> createGrid() {
        Grid<String[]> grid = new Grid<>();
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_COMPACT);
        grid.getStyle()
                .set("border-radius", "0.5rem")
                .set("border", "1px solid #e2e8f0");
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

    private byte[] convertAllTablesToMultiSheetExcel(Map<String, String> sheetMap) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (Map.Entry<String, String> entry : sheetMap.entrySet()) {
                String sheetName = entry.getKey();
                String rawData = entry.getValue();

                // Excel sheet names cannot exceed 31 chars or contain illegal chars
                String safeSheetName = sheetName.toLowerCase().replaceAll("[\\\\/*?:|\\[\\]\\s]+", "_");
                if (safeSheetName.length() > 31) {
                    safeSheetName = safeSheetName.substring(0, 31);
                }

                Sheet sheet = workbook.createSheet(safeSheetName);
                
                if (rawData != null && !rawData.trim().isEmpty()) {
                    String[] lines = rawData.split("\n");
                    for (int rowIndex = 0; rowIndex < lines.length; rowIndex++) {
                        Row row = sheet.createRow(rowIndex);
                        String[] columns = lines[rowIndex].split("\t"); // Tab-separated raw data
                        
                        for (int colIndex = 0; colIndex < columns.length; colIndex++) {
                            Cell cell = row.createCell(colIndex);
                            cell.setCellValue(columns[colIndex].trim());
                        }
                    }
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
    
    private VerticalLayout buildDataView(
            String titleText, 
            Grid<String[]> grid, 
            String headerTitle, 
            String[] headers, 
            String rawData,
            byte[] cachedExcel) {

        VerticalLayout card = createBaseCard();

        H2 header = new H2(titleText);
        header.getStyle()
                .set("font-size", "1.25rem")
                .set("font-weight", "700")
                .set("color", "#0f172a")
                .set("margin", "0");

        // Export Excel Button (Exports pre-built multi-sheet Excel instantly)
        Button downloadExcelBtn = new Button("Excel Export", VaadinIcon.FILE_TABLE.create());
        downloadExcelBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        downloadExcelBtn.getStyle().set("border-radius", "0.375rem");

        String excelFileName = "imsr_extraction.xlsx";
        StreamResource excelResource = new StreamResource(excelFileName,
                () -> new ByteArrayInputStream(cachedExcel != null ? cachedExcel : new byte[0]));

        excelResource.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        excelResource.setCacheTime(0);

        Anchor downloadExcelAnchor = new Anchor(excelResource, "");
        downloadExcelAnchor.getElement().setAttribute("download", true);
        downloadExcelAnchor.add(downloadExcelBtn);

        // Export CSV Button (Keeps single-table CSV export)
        Button downloadCsvBtn = new Button("Csv Export", VaadinIcon.DOWNLOAD.create());
        downloadCsvBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        downloadCsvBtn.getStyle().set("border-radius", "0.375rem");

        String csvData = rawData != null ? rawData.replace("\t", ",") : "";
        StreamResource csvResource = new StreamResource(headerTitle.toLowerCase().replace(" ", "_") + ".csv",
                () -> new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8)));

        Anchor downloadCsvAnchor = new Anchor(csvResource, "");
        downloadCsvAnchor.setTarget("_blank");
        downloadCsvAnchor.add(downloadCsvBtn);

        HorizontalLayout toolbar = new HorizontalLayout(header, new HorizontalLayout(downloadExcelAnchor, downloadCsvAnchor));
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        card.add(toolbar, grid);
        card.setFlexGrow(1, grid);
        return card;
    }

    private VerticalLayout buildAboutView() {
        VerticalLayout container = new VerticalLayout();
        container.setSizeFull();
        container.setAlignItems(Alignment.CENTER);

        VerticalLayout card = createBaseCard();
        card.setMaxWidth("900px");

        H2 header = new H2("Project Team");
        header.getStyle()
                .set("font-size", "1.5rem")
                .set("font-weight", "700")
                .set("color", "#0f172a")
                .set("margin-bottom", "1rem");

        VerticalLayout memberList = new VerticalLayout();
        memberList.setPadding(false);
        memberList.setSpacing(true);

        memberList.add(
                createMemberRow("Dung Nguyen", "Developer", "Research Scientist II - Colorado State University", "https://dzungcsu.wixsite.com/operations-research/about"),
                createMemberRow("Yu Wei", "Research Collaborator", "Professor - Colorado State University", "https://people.warnercnr.colostate.edu/?yu.wei"),
                createMemberRow("Erin Belval", "Research Collaborator", "Research Forester - USDA Forest Service", "https://research.fs.usda.gov/about/people/erin.belval"),
                createMemberRow("Karen Short", "Research Collaborator", "Research Ecologist - USDA Forest Service", "https://research.fs.usda.gov/about/people/karen.c.short"),
                createMemberRow("David Calkin", "Research Collaborator", "Former Research Forester - USDA Forest Service", "https://research.fs.usda.gov/about/people/dave.e.calkin")
        );

        card.add(header, memberList);
        container.add(card);
        return container;
    }

    private Div createMemberRow(String name, String role, String affiliation, String url) {
        Div row = new Div();
        row.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("width", "100%")
                .set("padding", "1rem")
                .set("border-radius", "0.5rem")
                .set("background-color", "#f8fafc")
                .set("border", "1px solid #e2e8f0");

        HorizontalLayout left = new HorizontalLayout();
        left.setAlignItems(Alignment.CENTER);

        Icon avatar = VaadinIcon.USER.create();
        avatar.getStyle()
                .set("padding", "0.5rem")
                .set("background-color", "#e0f2fe")
                .set("color", "#0284c7")
                .set("border-radius", "9999px")
                .set("margin-right", "0.75rem");

        VerticalLayout details = new VerticalLayout();
        details.setPadding(false);
        details.setSpacing(false);

        Anchor link = new Anchor(url, name);
        link.setTarget("_blank");
        link.getStyle()
                .set("font-weight", "700")
                .set("color", "#0284c7")
                .set("text-decoration", "underline")
                .set("font-size", "1.05rem")
                .set("cursor", "pointer");

        Span affiliationSpan = new Span(affiliation);
        affiliationSpan.getStyle().set("font-size", "0.85rem").set("color", "#64748b");

        details.add(link, affiliationSpan);
        left.add(avatar, details);

        Span roleBadge = new Span(role);
        roleBadge.getStyle()
                .set("background-color", "#f1f5f9")
                .set("color", "#475569")
                .set("font-size", "0.75rem")
                .set("font-weight", "600")
                .set("padding", "0.25rem 0.75rem")
                .set("border-radius", "9999px")
                .set("border", "1px solid #cbd5e1");

        row.add(left, roleBadge);
        return row;
    }

    private VerticalLayout buildLicenseView() {
        VerticalLayout container = new VerticalLayout();
        container.setSizeFull();
        container.setAlignItems(Alignment.CENTER);

        VerticalLayout card = createBaseCard();
        card.setMaxWidth("900px");

        H2 header = new H2("Software License");
        header.getStyle()
                .set("font-size", "1.5rem")
                .set("font-weight", "700")
                .set("color", "#0f172a")
                .set("margin", "0");

        Span badge = new Span("GNU GPLv3 License");
        badge.getStyle()
                .set("background-color", "#dcfce7")
                .set("color", "#15803d")
                .set("font-weight", "700")
                .set("font-size", "0.75rem")
                .set("padding", "0.25rem 0.75rem")
                .set("border-radius", "9999px");

        HorizontalLayout titleBox = new HorizontalLayout(header, badge);
        titleBox.setAlignItems(Alignment.CENTER);
        titleBox.setWidthFull();
        titleBox.setJustifyContentMode(JustifyContentMode.BETWEEN);

        String htmlContent = "Copyright (C) 2026 IMSR-WEBAPP DEVELOPER<br><br>"
                + "IMSR-WEBAPP is free online software: you can redistribute it and/or modify "
                + "it under the terms of the GNU General Public License as published by "
                + "the Free Software Foundation, either version 3 of the License, or "
                + "(at your option) any later version.<br><br>"
                + "IMSR-WEBAPP is distributed in the hope that it will be useful, "
                + "but WITHOUT ANY WARRANTY; without even the implied warranty of "
                + "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the "
                + "GNU General Public License for more details.<br><br>"
                + "You should have received a copy of the GNU General Public License "
                + "along with IMSR-WEBAPP. If not, see <a href=\"http://www.gnu.org/licenses/\" target=\"_blank\" style=\"color: #0284c7; text-decoration: underline;\">http://www.gnu.org/licenses/</a>.<br><br>";
//                + "----------------------------------------------------------------------------------------------------<br>"
//                + "If you use data generated by this webapp, please cite our paper:<br>"
//                + "Nguyen, D., Belval, E.J., Wei, Y. et al. Dataset of United States Incident Management Situation Reports from 2007 to 2021. "
//                + "Sci Data 11, 23 (2024). <a href=\"https://doi.org/10.1038/s41597-023-02876-8\" target=\"_blank\" style=\"color: #0284c7; text-decoration: underline;\">https://doi.org/10.1038/s41597-023-02876-8</a>";

        Html licenseDisplay = new Html("<div style=\""
                + "background-color: #f8fafc; "
                + "color: #1e293b; "
                + "padding: 1.5rem; "
                + "border-radius: 0.5rem; "
                + "font-family: 'Fira Code', Monaco, monospace; "
                + "font-size: 0.875rem; "
                + "line-height: 1.6; "
                + "white-space: pre-wrap; "
                + "border: 1px solid #cbd5e1; "
                + "width: 100%;"
                + "\">" + htmlContent + "</div>");

        card.add(titleBox, licenseDisplay);
        container.add(card);
        return container;
    }
    
    private VerticalLayout buildArchiveView() {
        VerticalLayout container = new VerticalLayout();
        container.setSizeFull();
        container.setAlignItems(Alignment.CENTER);

        VerticalLayout card = createBaseCard();
        card.setMaxWidth("900px");

        H2 header = new H2("Latest Extraction Archive");
        header.getStyle()
                .set("font-size", "1.5rem")
                .set("font-weight", "700")
                .set("color", "#0f172a")
                .set("margin", "0");

        HorizontalLayout titleBox = new HorizontalLayout(header);
        titleBox.setAlignItems(Alignment.CENTER);
        titleBox.setWidthFull();
        titleBox.setJustifyContentMode(JustifyContentMode.BETWEEN);

        String htmlContent =
                "2007-2025 IMSR data: <a href=\"https://doi.org/10.6084/m9.figshare.31032004\" target=\"_blank\" style=\"color: #0284c7; text-decoration: underline;\">https://doi.org/10.6084/m9.figshare.31032004</a>.<br>"
                + "----------------------------------------------------------------------------------------------------<br>"
                + "If you use data generated by this webapp or the above archives, please cite our paper:<br>"
                + "Nguyen, D., Belval, E.J., Wei, Y. et al. Dataset of United States Incident Management Situation Reports from 2007 to 2021. "
                + "Sci Data 11, 23 (2024). <a href=\"https://doi.org/10.1038/s41597-023-02876-8\" target=\"_blank\" style=\"color: #0284c7; text-decoration: underline;\">https://doi.org/10.1038/s41597-023-02876-8</a>";

        Html licenseDisplay = new Html("<div style=\""
                + "background-color: #f8fafc; "
                + "color: #1e293b; "
                + "padding: 1.5rem; "
                + "border-radius: 0.5rem; "
                + "font-family: 'Fira Code', Monaco, monospace; "
                + "font-size: 0.875rem; "
                + "line-height: 1.6; "
                + "white-space: pre-wrap; "
                + "border: 1px solid #cbd5e1; "
                + "width: 100%;"
                + "\">" + htmlContent + "</div>");

        card.add(titleBox, licenseDisplay);
        container.add(card);
        return container;
    }

    private VerticalLayout createBaseCard() {
        VerticalLayout card = new VerticalLayout();
        card.setSizeFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("background-color", "#ffffff")
                .set("border-radius", "0.75rem")
                .set("box-shadow", "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.05)")
                .set("border", "1px solid #e2e8f0");
        return card;
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
                    
                    logBuilder.append("If you use data generated by this webapp, please cite our paper:\n");
					logBuilder.append("Nguyen, D., Belval, E.J., Wei, Y. et al. Dataset of United States Incident Management Situation Reports from 2007 to 2021. "
							+ "Sci Data 11, 23 (2024). https://doi.org/10.1038/s41597-023-02876-8\n");
					logBuilder.append("----------------------------------------------------------------------------------------------------\n");
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

                        Map<String, String> sheetMap = new java.util.LinkedHashMap<>();
                        sheetMap.put("National Activity", nationalTsvData);
                        sheetMap.put("GACC Activity", gaccTsvData);
                        sheetMap.put("Wildfire Activity", wildfireTsvData);
                        sheetMap.put("Resource Summary", resourceTsvData);

                        this.cachedExcelBytes = convertAllTablesToMultiSheetExcel(sheetMap);
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

        logBuilder.append("----------------------------------------------------------------------------------------------------\n");
        logBuilder.append("AGGREGATION IS COMPLETED - ALL RESULTS ARE READY FOR EXPORTATION\n");
        logBuilder.append("----------------------------------------------------------------------------------------------------\n");

        consoleOutput.setValue(logBuilder.toString());
        tabs.setSelectedTab(tabConsole);
        updateContent(tabConsole);
        Notification.show("Aggregation completed successfully!", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private File[] prepareAndCleanFiles(File[] pdfFiles) {
        if (pdfFiles == null || pdfFiles.length == 0) return new File[0];

        Map<String, File> sortedUniqueFiles = new TreeMap<>();

        for (File file : pdfFiles) {
            try {
                String fileName = file.getName();
                // Returns formatted YYYYMMDD string
                String dateKey = extractDateString(fileName); 
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
                sortedUniqueFiles.putIfAbsent(file.getName(), file);
            }
        }

        return sortedUniqueFiles.values().toArray(new File[0]);
    }

    private String extractDateString(String fileName) {
        // 1. Check if it's already in clean YYYYMMDDIMSR format (e.g., 20260619IMSR.pdf)
        java.util.regex.Pattern alreadyCleanPattern = java.util.regex.Pattern.compile("^(\\d{8})IMSR");
        java.util.regex.Matcher cleanMatcher = alreadyCleanPattern.matcher(fileName);
        if (cleanMatcher.find()) {
            return cleanMatcher.group(1);
        }

        // 2. Strip prefix up to "IMSR" for raw names (e.g., IMSR_CY26_06192026.pdf)
        if (fileName.contains("IMSR")) {
            fileName = fileName.substring(fileName.indexOf("IMSR"));
        }

        // 3. Match 8-digit block and convert MMDDYYYY -> YYYYMMDD
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{8})");
        java.util.regex.Matcher matcher = pattern.matcher(fileName);

        if (matcher.find()) {
            String mmddyyyy = matcher.group(1);

            String mm = mmddyyyy.substring(0, 2);
            String dd = mmddyyyy.substring(2, 4);
            String yyyy = mmddyyyy.substring(4, 8);

            return yyyy + mm + dd;
        }

        throw new IllegalArgumentException("Could not find a valid 8-digit date pattern in filename: " + fileName);
    }
    
    private File fetchAndRenameTodayIMSR() {
        try {
            // Build YYYYMMDDIMSR.pdf filename
            String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fileName = datePrefix + "IMSR.pdf";

            // Setup destination in temporary directory
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "auto_imsr_downloads");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            File targetFile = new File(tempDir, fileName);

            // Download from NIFC
            URI uri = URI.create("https://www.nifc.gov/nicc-files/sitreprt.pdf");
            try (InputStream in = uri.toURL().openStream()) {
                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            return targetFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void fix_ctd(List<String> fires) {
        // Implementation for fixing cost-to-date or date formatting logic
    }

    private void updateContent(Tab selectedTab) {
        contentArea.removeAll();
        
        if (selectedTab.equals(tabNational)) {
            contentArea.add(buildDataView("National Activity Data", nationalGrid, "National Activity", header1, nationalTsvData, cachedExcelBytes));
        } else if (selectedTab.equals(tabGacc)) {
            contentArea.add(buildDataView("GACC Activity Data", gaccGrid, "GACC Activity", header2, gaccTsvData, cachedExcelBytes));
        } else if (selectedTab.equals(tabWildfire)) {
            contentArea.add(buildDataView("Wildfire Activity Data", wildfireGrid, "Wildfire Activity", header3, wildfireTsvData, cachedExcelBytes));
        } else if (selectedTab.equals(tabResource)) {
            contentArea.add(buildDataView("Resource Summary Data", resourceGrid, "Resource Summary", header4, resourceTsvData, cachedExcelBytes));
        } else if (selectedTab.equals(tabConsole)) {
            contentArea.add(consoleOutput);
        } else if (selectedTab.equals(tabAbout)) {
            contentArea.add(buildAboutView());
        } else if (selectedTab.equals(tabLicense)) {
            contentArea.add(buildLicenseView());
        } else if (selectedTab.equals(tabArchive)) {
            contentArea.add(buildArchiveView());
        }
    }
}
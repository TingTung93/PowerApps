package org.commercialtracking;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CommercialTrackingApp extends JFrame {
    private final AppConfig config;
    private EventStore store;
    private final BarcodeParserChain parser = new BarcodeParserChain();
    private final Projection projection = new Projection();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final String sessionId = UUID.randomUUID().toString();
    private final List<TrackingEvent> allEvents = new ArrayList<TrackingEvent>();
    private final List<TrackingEvent> sessionEvents = new ArrayList<TrackingEvent>();

    private final JComboBox<String> mode = new JComboBox<String>(new String[]{"Inbound", "Outbound"});
    private final JComboBox<String> location = new JComboBox<String>(
            new String[]{"Main Receiving", "Loading Dock", "Mailroom", "Warehouse"});
    private final JTextField recipient = new JTextField();
    private final JTextField scan = new JTextField();
    private final JLabel result = new JLabel("Ready for the next scan.");
    private final JLabel sync = new JLabel("Not loaded");
    private final DefaultTableModel sessionModel = tableModel();
    private final DefaultTableModel historyModel = tableModel();
    private final JTable historyTable = new JTable(historyModel);
    private final JTextArea conflicts = new JTextArea();
    private final JTextArea diagnostics = new JTextArea();
    private volatile boolean busy;

    private CommercialTrackingApp(AppConfig config) {
        super("Commercial Tracking RC");
        this.config = config;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setSize(1180, 760);
        setLocationRelativeTo(null);
        buildUi();
        configureRootIfNeeded();
        reloadAsync();
        new Timer(15000, e -> reloadAsync()).start();
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--self-test".equals(args[0])) {
            try {
                SelfTest.run();
                System.out.println("CommercialTracking RC self-test: PASS");
            } catch (Exception ex) {
                System.err.println("CommercialTracking RC self-test: FAIL - " + ex.getMessage());
                ex.printStackTrace(System.err);
                System.exit(1);
            }
            return;
        }
        if (args.length == 0 || !"--classic-ui".equals(args[0])) {
            try {
                new BrowserServer(AppConfig.load()).startAndOpen();
            } catch (Exception ex) {
                System.err.println("Modern UI failed: " + ex.getMessage());
                System.err.println("Run with --classic-ui to use the Swing fallback.");
                System.exit(1);
            }
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            try {
                new CommercialTrackingApp(AppConfig.load()).setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Startup failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(10, 5));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        JLabel title = new JLabel("Commercial Package Tracking — Release Candidate");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        header.add(sync, BorderLayout.EAST);

        JPanel controls = new JPanel(new GridLayout(2, 4, 8, 5));
        controls.setBorder(BorderFactory.createTitledBorder("Receiving context"));
        controls.add(new JLabel("Mode"));
        controls.add(new JLabel("Location / stream"));
        controls.add(new JLabel("Recipient (optional inbound)"));
        controls.add(new JLabel("Shared folder"));
        controls.add(mode);
        controls.add(location);
        controls.add(recipient);
        JButton folder = new JButton("Change...");
        folder.addActionListener(e -> chooseRoot());
        controls.add(folder);

        JPanel scanPanel = new JPanel();
        scanPanel.setLayout(new BoxLayout(scanPanel, BoxLayout.Y_AXIS));
        scanPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel scanLabel = new JLabel("Scan package barcode");
        scanLabel.setAlignmentX(LEFT_ALIGNMENT);
        scan.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 17));
        scan.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        scan.setAlignmentX(LEFT_ALIGNMENT);
        scan.addActionListener(this::submitScan);
        result.setOpaque(true);
        result.setBackground(Color.WHITE);
        result.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(160, 160, 160)),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        result.setAlignmentX(LEFT_ALIGNMENT);
        result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        scanPanel.add(scanLabel);
        scanPanel.add(Box.createVerticalStrut(4));
        scanPanel.add(scan);
        scanPanel.add(Box.createVerticalStrut(8));
        scanPanel.add(result);

        JTable sessionTable = new JTable(sessionModel);
        sessionTable.setAutoCreateRowSorter(true);
        historyTable.setAutoCreateRowSorter(true);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Current Session", new JScrollPane(sessionTable));
        tabs.addTab("Package History", new JScrollPane(historyTable));
        conflicts.setEditable(false);
        conflicts.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabs.addTab("Conflicts", new JScrollPane(conflicts));
        diagnostics.setEditable(false);
        diagnostics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabs.addTab("Diagnostics", new JScrollPane(diagnostics));

        JPanel actions = new JPanel();
        JButton refresh = new JButton("Refresh shared events");
        refresh.addActionListener(e -> reloadAsync());
        JButton manifest = new JButton("Print inbound session manifest");
        manifest.addActionListener(e -> createManifest());
        JButton assign = new JButton("Assign recipient to selected package");
        assign.addActionListener(e -> assignRecipient());
        JButton voidPackage = new JButton("Void selected package");
        voidPackage.addActionListener(e -> voidSelectedPackage());
        JButton clear = new JButton("Close / clear local session");
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Clear this workstation's session view? Shared events remain.",
                    "Clear session", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                sessionEvents.clear();
                refreshTables();
            }
        });
        actions.add(refresh);
        actions.add(manifest);
        actions.add(assign);
        actions.add(voidPackage);
        actions.add(clear);

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.NORTH);
        top.add(controls, BorderLayout.CENTER);
        top.add(scanPanel, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    private void configureRootIfNeeded() {
        if (config.sharedRoot == null || !Files.isDirectory(config.sharedRoot)) chooseRoot();
        if (config.sharedRoot == null) throw new IllegalStateException("A synchronized folder is required.");
        try {
            store = new EventStore(config.sharedRoot, config.localRoot);
            config.save();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot initialize shared folder: " + ex.getMessage(), ex);
        }
    }

    private void chooseRoot() {
        JFileChooser chooser = new JFileChooser(config.sharedRoot == null ? null : config.sharedRoot.toFile());
        chooser.setDialogTitle("Select the synchronized Commercial Tracking folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            config.sharedRoot = chooser.getSelectedFile().toPath();
            store = new EventStore(config.sharedRoot, config.localRoot);
            config.save();
            reloadAsync();
        } catch (IOException ex) {
            error("Folder could not be used: " + ex.getMessage());
        }
    }

    private void submitScan(ActionEvent ignored) {
        if (busy) return;
        String raw = scan.getText();
        if (raw.trim().length() == 0) { warning("No barcode data was received."); return; }
        if ("Inbound".equals(mode.getSelectedItem()) && location.getSelectedItem() == null) {
            warning("Select a receiving location before scanning.");
            return;
        }
        ParseResult parsed = parser.parse(raw);
        if (!parsed.isSuccess()) {
            fail("No supported tracking number was found. Source: " + parsed.getSource());
            return;
        }
        if (parsed.isConfirmationRequired()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Confirm tracking number?\n\n" + parsed.getTrackingNumber() + "\nCarrier: "
                            + parsed.getCarrier() + "\nConfidence: " + parsed.getConfidence(),
                    "Confirm parsed barcode", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) { scan.selectAll(); scan.requestFocusInWindow(); return; }
        }
        PackageState current = projection.find(parsed.getTrackingNumber());
        TrackingEvent event = createEvent(parsed, current);
        if (event == null) return;
        busy = true;
        scan.setEnabled(false);
        status("SAVING", "Saving locally and submitting to the synchronized folder...", new Color(222, 235, 247));
        io.submit(() -> {
            try {
                store.append(event);
                SwingUtilities.invokeLater(() -> {
                    sessionEvents.add(event);
                    allEvents.add(event);
                    projection.replay(allEvents);
                    refreshTables();
                    busy = false;
                    scan.setEnabled(true);
                    scan.setText("");
                    scan.requestFocusInWindow();
                    if ("PACKAGE_LOCATION_CHANGED".equals(event.eventType)) {
                        warning("Already active. Location update submitted for " + event.trackingNumber + ".");
                    } else {
                        success(("PACKAGE_RELEASED".equals(event.eventType) ? "Package released: " : "Package received: ")
                                + event.trackingNumber);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    scan.setEnabled(true);
                    scan.selectAll();
                    scan.requestFocusInWindow();
                    fail("Event was not submitted: " + ex.getMessage());
                });
            }
        });
    }

    private TrackingEvent createEvent(ParseResult parsed, PackageState current) {
        TrackingEvent event = new TrackingEvent();
        event.deviceId = config.deviceId;
        event.sessionId = sessionId;
        event.actor = config.actor;
        event.actorDisplayName = config.actorDisplayName;
        event.trackingNumber = parsed.getTrackingNumber();
        event.carrier = parsed.getCarrier();
        event.parserSource = parsed.getSource();
        event.parserConfidence = parsed.getConfidence().name();
        event.recipient = recipient.getText().trim();
        event.location = String.valueOf(location.getSelectedItem());
        event.streamId = event.location.toUpperCase().replaceAll("[^A-Z0-9]+", "-");
        event.weight = value(parsed.getMetadata(), "weight");
        event.packageCount = value(parsed.getMetadata(), "packageCount");
        event.addressee = value(parsed.getMetadata(), "addressee");
        event.address = value(parsed.getMetadata(), "shipToAddress1");

        if ("Outbound".equals(mode.getSelectedItem())) {
            if (current == null || !"READY_FOR_PICKUP".equals(current.status)) {
                warning("No active package awaiting pickup was found in the synchronized event view.");
                return null;
            }
            event.eventType = "PACKAGE_RELEASED";
            event.status = "PICKED_UP";
            if (event.recipient.length() == 0) event.recipient = current.recipient;
            event.location = current.location;
            event.notes = "Outbound release";
        } else if (current != null && "READY_FOR_PICKUP".equals(current.status)) {
            event.eventType = "PACKAGE_LOCATION_CHANGED";
            event.status = "READY_FOR_PICKUP";
            event.notes = "Repeat inbound scan; active package retained";
        } else {
            event.eventType = "PACKAGE_RECEIVED";
            event.status = "READY_FOR_PICKUP";
            event.notes = value(parsed.getMetadata(), "labelType");
        }
        return event;
    }

    private void reloadAsync() {
        if (store == null) return;
        sync.setText("Refreshing...");
        io.submit(() -> {
            EventStore.LoadResult loaded = store.loadAll();
            SwingUtilities.invokeLater(() -> {
                allEvents.clear();
                allEvents.addAll(loaded.events);
                projection.replay(allEvents);
                diagnostics.setText(loaded.errors.isEmpty() ? "No malformed shared events.\nShared root: "
                        + store.getSharedRoot() : join(loaded.errors));
                sync.setText("Observed " + allEvents.size() + " events | " + Instant.now().toString());
                refreshTables();
            });
        });
    }

    private void refreshTables() {
        sessionModel.setRowCount(0);
        for (int i = sessionEvents.size() - 1; i >= 0; i--) addEventRow(sessionModel, sessionEvents.get(i));
        historyModel.setRowCount(0);
        for (PackageState p : projection.all()) {
            historyModel.addRow(new Object[]{p.lastEventUtc, p.trackingNumber, p.carrier, p.location,
                    p.recipient, p.status, p.lastDevice});
        }
        conflicts.setText(projection.conflicts().isEmpty() ? "No conflicts detected." : join(projection.conflicts()));
    }

    private void addEventRow(DefaultTableModel model, TrackingEvent e) {
        model.addRow(new Object[]{e.occurredUtc, e.trackingNumber, e.carrier, e.location,
                e.recipient, e.status, e.deviceId});
    }

    private void createManifest() {
        List<TrackingEvent> inbound = new ArrayList<TrackingEvent>();
        for (TrackingEvent event : sessionEvents) {
            if ("PACKAGE_RECEIVED".equals(event.eventType) || "PACKAGE_LOCATION_CHANGED".equals(event.eventType))
                inbound.add(event);
        }
        if (inbound.isEmpty()) { warning("No inbound events are present in this session."); return; }
        try {
            Path output = new ManifestWriter().write(store.getSharedRoot(), String.valueOf(location.getSelectedItem()), inbound);
            success("Manifest created: " + output.getFileName());
        } catch (Exception ex) {
            fail("Manifest could not be created: " + ex.getMessage());
        }
    }

    private void assignRecipient() {
        String tracking = selectedHistoryTracking();
        if (tracking == null) return;
        String name = JOptionPane.showInputDialog(this, "Recipient for " + tracking + ":",
                recipient.getText().trim());
        if (name == null || name.trim().length() == 0) return;
        PackageState current = projection.find(tracking);
        TrackingEvent event = manualEvent("RECIPIENT_ASSIGNED", current, name.trim(), "Recipient assigned");
        appendManualEvent(event, "Recipient assignment submitted for " + tracking + ".");
    }

    private void voidSelectedPackage() {
        String tracking = selectedHistoryTracking();
        if (tracking == null) return;
        PackageState current = projection.find(tracking);
        if (current == null || "VOIDED".equals(current.status)) {
            warning("The selected package is not active or is already voided.");
            return;
        }
        String reason = JOptionPane.showInputDialog(this,
                "Type a reason to void " + tracking + ". The audit history will be retained:");
        if (reason == null || reason.trim().length() == 0) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "Confirm void of " + tracking + "?\n\nReason: " + reason,
                "Confirm void", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        TrackingEvent event = manualEvent("PACKAGE_VOIDED", current,
                current.recipient, "Void reason: " + reason.trim());
        event.status = "VOIDED";
        appendManualEvent(event, "Void submitted for " + tracking + ".");
    }

    private String selectedHistoryTracking() {
        int viewRow = historyTable.getSelectedRow();
        if (viewRow < 0) {
            warning("Select a package in Package History first.");
            return null;
        }
        int modelRow = historyTable.convertRowIndexToModel(viewRow);
        return String.valueOf(historyModel.getValueAt(modelRow, 1));
    }

    private TrackingEvent manualEvent(String type, PackageState current, String assignedRecipient, String notes) {
        TrackingEvent event = new TrackingEvent();
        event.eventType = type;
        event.deviceId = config.deviceId;
        event.sessionId = sessionId;
        event.actor = config.actor;
        event.actorDisplayName = config.actorDisplayName;
        event.trackingNumber = current.trackingNumber;
        event.carrier = current.carrier;
        event.location = current.location;
        event.streamId = current.location.toUpperCase().replaceAll("[^A-Z0-9]+", "-");
        event.recipient = assignedRecipient;
        event.status = current.status;
        event.notes = notes;
        event.parserSource = "MANUAL_WORKFLOW";
        event.parserConfidence = "VERIFIED";
        return event;
    }

    private void appendManualEvent(TrackingEvent event, String successMessage) {
        if (busy) return;
        busy = true;
        io.submit(() -> {
            try {
                store.append(event);
                SwingUtilities.invokeLater(() -> {
                    allEvents.add(event);
                    sessionEvents.add(event);
                    projection.replay(allEvents);
                    refreshTables();
                    busy = false;
                    success(successMessage);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    fail("Operation was not submitted: " + ex.getMessage());
                });
            }
        });
    }

    private static DefaultTableModel tableModel() {
        return new DefaultTableModel(new Object[]{"UTC time", "Tracking", "Carrier", "Location",
                "Recipient", "Status", "Device"}, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private void success(String message) {
        Toolkit.getDefaultToolkit().beep();
        status("SUCCESS", message, new Color(223, 246, 221));
    }

    private void warning(String message) { status("WARNING", message, new Color(255, 244, 206)); }
    private void fail(String message) {
        Toolkit.getDefaultToolkit().beep();
        status("ERROR", message, new Color(253, 231, 233));
    }
    private void error(String message) { JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE); }
    private void status(String kind, String message, Color color) {
        result.setText("<html><b>" + kind + "</b> — " + html(message) + "</html>");
        result.setBackground(color);
    }
    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    private static String value(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null ? "" : value;
    }
    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) out.append(value).append('\n');
        return out.toString();
    }
}

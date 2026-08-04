package org.medsupply;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Instant;

public final class SwingApp extends JFrame {
    private final AppService service;
    private final JTextField folder = new JTextField();
    private final JTextField scan = new JTextField();
    private final JTextField qty = new JTextField("1", 4);
    private final JLabel status = new JLabel("Select a synchronized folder to begin.");
    private final JLabel kpis = new JLabel(" ");
    private final DefaultTableModel model =
            new DefaultTableModel(new Object[] {"Name", "GTIN", "Lot", "Expiration", "Qty"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };

    private SwingApp(AppService service) {
        super("Medical Supply Tracking (classic)");
        this.service = service;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        build();
        if (service.configured()) refresh();
        javax.swing.Timer refreshTimer = new javax.swing.Timer(15000, event -> {
            if (service.configured()) refresh();
        });
        refreshTimer.setRepeats(true);
        refreshTimer.start();
    }

    public static void launch(AppService service) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new SwingApp(service).setVisible(true));
    }

    static Object[] stockRow(StockLine line) {
        return new Object[] {line.name, line.gtin, line.lot, line.expirationIso, Integer.valueOf(line.quantity)};
    }

    private void build() {
        JPanel top = new JPanel(new GridLayout(2, 1, 6, 6));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderRow.add(new JLabel("Folder:"), BorderLayout.WEST);
        folderRow.add(folder, BorderLayout.CENTER);
        JButton choose = new JButton("Choose...");
        choose.addActionListener(e -> chooseFolder());
        JButton report = new JButton("Export report");
        report.addActionListener(e -> exportReport());
        JPanel folderButtons = new JPanel();
        folderButtons.add(choose);
        folderButtons.add(report);
        folderRow.add(folderButtons, BorderLayout.EAST);
        JPanel scanRow = new JPanel(new BorderLayout(6, 0));
        scanRow.add(new JLabel("Scan:"), BorderLayout.WEST);
        scanRow.add(scan, BorderLayout.CENTER);
        JPanel scanEast = new JPanel();
        scanEast.add(new JLabel("Qty"));
        scanEast.add(qty);
        JButton receive = new JButton("Receive");
        receive.addActionListener(e -> receive());
        scanEast.add(receive);
        scanRow.add(scanEast, BorderLayout.EAST);
        scan.addActionListener(e -> receive());
        top.add(folderRow);
        top.add(scanRow);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        bottom.add(kpis, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            service.configure(chooser.getSelectedFile().toPath());
            folder.setText(chooser.getSelectedFile().toString());
            refresh();
            status.setText("Folder configured.");
        } catch (Exception ex) {
            status.setText("Folder error: " + ex.getMessage());
        }
    }

    private void receive() {
        if (!service.configured()) { status.setText("Choose a folder first."); return; }
        try {
            int quantity = Integer.parseInt(qty.getText().trim());
            java.util.Map<String, Object> result = service.receive(scan.getText().trim(), quantity, false);
            if (Boolean.TRUE.equals(result.get("needsRegistration"))) {
                String gtin = String.valueOf(result.get("gtin"));
                String name = JOptionPane.showInputDialog(this, "Unknown product " + gtin + ". Product name:");
                if (name == null || name.trim().length() == 0) return;
                service.registerProduct(gtin, name.trim(), "", "", 0.0, -1, "", "MANUAL");
                service.receive(scan.getText().trim(), quantity, true);
            }
            scan.setText("");
            refresh();
            status.setText("Received.");
        } catch (Exception ex) {
            status.setText("Error: " + ex.getMessage());
        }
    }

    private void exportReport() {
        if (!service.configured()) { status.setText("Choose a folder first."); return; }
        try {
            Instant now = Instant.now();
            ManagementReport.Result r = service.writeManagementReport(now);
            status.setText("Report written: " + r.html.getFileName());
        } catch (Exception ex) {
            status.setText("Report error: " + ex.getMessage());
        }
    }

    private void refresh() {
        service.reload();
        model.setRowCount(0);
        for (StockLine line : service.stock()) if (line.active) model.addRow(stockRow(line));
        DashboardMetrics m = service.dashboard(Instant.now());
        kpis.setText("SKUs " + m.distinctSkus + "  |  Value " + String.format("%.2f", m.onHandValue)
                + "  |  Expired " + m.expired + "  |  Expiring 30d " + m.expiring30 + "  |  Out " + m.outOfStock);
    }
}

package org.medsupply;

public final class MedicalSupplyApp {
    private MedicalSupplyApp() {}

    public static void main(String[] args) {
        if (args.length > 0 && "--self-test".equals(args[0])) {
            try {
                SelfTest.run();
                System.out.println("MedicalSupply self-test: PASS");
            } catch (Exception ex) {
                System.err.println("MedicalSupply self-test: FAIL - " + ex.getMessage());
                ex.printStackTrace(System.err);
                System.exit(1);
            }
            return;
        }
        if (args.length > 0 && "--classic-ui".equals(args[0])) {
            try {
                AppConfig config = AppConfig.load();
                GudidClient gudid = config.gudidEnabled
                        ? new GudidClient(config.gudidEndpoint, new HttpsFetcher()) : null;
                SwingApp.launch(new AppService(config, gudid));
            } catch (Exception ex) {
                System.err.println("Medical Supply classic UI failed: " + ex.getMessage());
                System.exit(1);
            }
            return;
        }
        try {
            AppConfig config = AppConfig.load();
            GudidClient gudid = config.gudidEnabled
                    ? new GudidClient(config.gudidEndpoint, new HttpsFetcher()) : null;
            new BrowserServer(new AppService(config, gudid), config).startAndOpen();
        } catch (Exception ex) {
            System.err.println("Medical Supply UI failed: " + ex.getMessage());
            System.exit(1);
        }
    }
}

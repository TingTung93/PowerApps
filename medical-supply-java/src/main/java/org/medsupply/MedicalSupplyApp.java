package org.medsupply;
public final class MedicalSupplyApp {
    private MedicalSupplyApp() {}
    public static void main(String[] args) {
        if (args.length > 0 && "--self-test".equals(args[0])) {
            try { SelfTest.run(); System.out.println("MedicalSupply self-test: PASS"); }
            catch (Exception ex) { System.err.println("MedicalSupply self-test: FAIL - " + ex.getMessage()); ex.printStackTrace(System.err); System.exit(1); }
            return;
        }
        System.out.println("MedicalSupply foundation build. Use --self-test.");
    }
}

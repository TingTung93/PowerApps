package org.medsupply;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AppConfigTest {
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("medsupply-cfg");
        System.setProperty("medsupply.localBase", base.toString());

        AppConfig config = AppConfig.load();
        check(config.gudidEnabled, "gudid default on");
        check(config.reorderWindowDays == 90, "window default");
        check(config.staleDays == 30, "stale default");
        check(config.gudidEndpoint.startsWith("https://accessgudid"), "endpoint default");
        String authenticatedActor = config.actor;

        config.sharedRoot = base.resolve("shared");
        config.reorderWindowDays = 45;
        config.gudidEnabled = false;
        config.actor = "forged-user";
        config.save();

        String actualJavaUser = System.getProperty("user.name");
        System.setProperty("user.name", "forged-user");
        AppConfig reloaded = AppConfig.load();
        if (actualJavaUser == null) System.clearProperty("user.name");
        else System.setProperty("user.name", actualJavaUser);
        check(base.resolve("shared").equals(reloaded.sharedRoot), "shared persisted");
        check(reloaded.reorderWindowDays == 45, "window persisted");
        check(!reloaded.gudidEnabled, "gudid persisted");
        check(!"forged-user".equals(reloaded.actor), "actor is OS-derived, not persisted");
        check(authenticatedActor.equals(reloaded.actor), "actor ignores forged Java user property");
        check(!Files.exists(base.resolve("MedicalSupply/config/client.json.tmp")), "no config temp left");
        System.out.println("AppConfigTest: PASS");
    }

    private static void check(boolean cond, String label) {
        if (!cond) throw new AssertionError("Failed: " + label);
    }
}

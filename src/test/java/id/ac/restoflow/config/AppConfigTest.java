package id.ac.restoflow.config;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
    @Test void connectionPersistsWithoutPasswordAndEnvironmentOverrides() throws Exception {
        var path=temporary.resolve("connection.properties");var config=new AppConfig("https://example.supabase.co","sb_publishable_example");
        config.save(path);assertEquals(config,AppConfig.load(path,Map.of()));
        assertEquals("https://other.supabase.co",AppConfig.load(path,Map.of("SUPABASE_URL","https://other.supabase.co")).url());
        assertFalse(config.toString().contains("sb_publishable"));
    }
    @Test void legacyAdministratorJwtIsRejected() {
        String body=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"service_role\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(new AppConfig("https://example.supabase.co","header."+body+".signature").isConfigured());
    }
    @Test void missingConfigurationIsNotReady() {
        assertFalse(AppConfig.fromEnvironment(Map.of()).isConfigured());
    }

    @Test void placeholdersAndSecretKeysAreRejected() {
        assertFalse(new AppConfig("https://YOUR_PROJECT_ID.supabase.co", "YOUR_KEY").isConfigured());
        assertFalse(new AppConfig("https://example.supabase.co", "sb_secret_example").isConfigured());
    }

    @Test void requiresHttpsOriginWithoutCredentialsOrQuery() {
        for (String url : new String[]{"http://example.supabase.co", "invalid", "https://user:pass@example.com",
                "https://example.com?key=value", "https://example.com/rest/v1", "https://example.com#fragment"}) {
            assertFalse(new AppConfig(url, "sb_publishable_example").isConfigured(), url);
        }
    }

    @Test void validLocalConfigurationDoesNotImplyConnectivity() {
        assertTrue(AppConfig.fromEnvironment(Map.of("SUPABASE_URL", " https://example.supabase.co/ ",
                "SUPABASE_PUBLISHABLE_KEY", " sb_publishable_example ")).isConfigured());
    }
}

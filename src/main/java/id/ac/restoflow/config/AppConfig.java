package id.ac.restoflow.config;

import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Base64;

/** Memeriksa kelengkapan lokal; pemeriksaan ini belum membuktikan koneksi ke Supabase. */
public record AppConfig(String url, String publishableKey) {
    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".restoflow", "connection.properties");
    }
    public static AppConfig load(Path file, Map<String, String> environment) throws IOException {
        Properties stored = new Properties();
        if (Files.exists(file)) try (var in = Files.newBufferedReader(file)) { stored.load(in); }
        return new AppConfig(environment.getOrDefault("SUPABASE_URL", stored.getProperty("url", "")).trim(),
                environment.getOrDefault("SUPABASE_PUBLISHABLE_KEY", stored.getProperty("key", "")).trim());
    }
    public void save(Path file) throws IOException {
        if (!isConfigured()) throw new IllegalArgumentException("Gunakan URL HTTPS dan publishable/anon key yang valid.");
        Properties data = new Properties(); data.setProperty("url", url); data.setProperty("key", publishableKey);
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (var out = Files.newBufferedWriter(file)) { data.store(out, "RestoFlow public connection configuration"); }
    }
    @Override public String toString() { return "AppConfig[credentials hidden]"; }
    public static AppConfig fromEnvironment(Map<String, String> environment) {
        return new AppConfig(environment.getOrDefault("SUPABASE_URL", "").trim(),
                environment.getOrDefault("SUPABASE_PUBLISHABLE_KEY", "").trim());
    }

    public boolean isConfigured() {
        if (url.isBlank() || publishableKey.isBlank() || url.contains("YOUR_")
                || publishableKey.contains("YOUR_") || !isPublicKey(publishableKey)) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
    private static boolean isPublicKey(String key) {
        if (key.startsWith("sb_publishable_")) return key.length() > 15;
        try {
            String[] parts = key.split("\\.");
            if (parts.length != 3) return false;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            return "anon".equals(id.ac.restoflow.integration.Json.read(payload).path("role").asText());
        } catch (RuntimeException ex) { return false; }
    }
}

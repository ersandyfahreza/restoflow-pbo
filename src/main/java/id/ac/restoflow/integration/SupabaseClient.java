package id.ac.restoflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import id.ac.restoflow.config.AppConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/** Sesi hanya di memori. Seluruh pemanggilan dijalankan di worker, bukan JavaFX thread. */
public final class SupabaseClient {
    private final AppConfig config;
    private final HttpClient http;
    private String accessToken = "";
    private String refreshToken = "";
    private Instant expiresAt = Instant.EPOCH;

    public SupabaseClient(AppConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        if (!config.isConfigured()) throw new IllegalArgumentException("URL dan publishable key tidak valid.");
    }

    // Transport dapat diganti dengan HTTP fixture dalam pengujian.
    SupabaseClient(AppConfig config, HttpClient http) { this.config = config; this.http = http; }

    public synchronized void login(String email, String password) {
        clearSession();
        JsonNode data = request("/auth/v1/token?grant_type=password",
                Json.object().put("email", email.trim()).put("password", password), "");
        acceptSession(data);
    }

    private void acceptSession(JsonNode data) {
        String access = data.path("access_token").asText();
        String refresh = data.path("refresh_token").asText();
        if (access.isBlank() || refresh.isBlank()) throw new IllegalStateException("Respons login tidak lengkap.");
        accessToken = access;
        refreshToken = refresh;
        expiresAt = Instant.now().plusSeconds(Math.max(0, data.path("expires_in").asLong(3600) - 60));
    }

    public synchronized JsonNode rpc(String function, JsonNode payload) {
        if (accessToken.isBlank()) throw new IllegalStateException("Silakan masuk terlebih dahulu.");
        if (!Instant.now().isBefore(expiresAt)) {
            try { acceptSession(request("/auth/v1/token?grant_type=refresh_token",
                    Json.object().put("refresh_token", refreshToken), "")); }
            catch (RuntimeException ex) { clearSession(); throw new IllegalStateException("Sesi berakhir. Silakan masuk ulang."); }
        }
        if (!function.matches("rf_[a-z_]+")) throw new IllegalArgumentException("Endpoint tidak valid.");
        return request("/rest/v1/rpc/" + function, payload, accessToken);
    }

    public synchronized void logout() {
        try { if (!accessToken.isBlank()) request("/auth/v1/logout?scope=local", Json.object(), accessToken); }
        finally { clearSession(); }
    }

    public synchronized void clearSession() {
        accessToken = ""; refreshToken = ""; expiresAt = Instant.EPOCH;
    }

    private JsonNode request(String path, JsonNode body, String token) {
        String base = config.url().replaceAll("/+$", "");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                .header("apikey", config.publishableKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)));
        if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                if (status == 401) throw new IllegalStateException("Email/password salah atau sesi tidak berlaku.");
                if (status == 403) throw new IllegalStateException("Akun tidak memiliki izin untuk tindakan ini.");
                if (status == 429) throw new IllegalStateException("Terlalu banyak permintaan. Tunggu sebelum mencoba ulang.");
                JsonNode error;
                try { error = Json.read(response.body()); } catch (RuntimeException ex) { error = Json.object(); }
                String message = error.path("message").asText("");
                // Hanya pesan bisnis yang sengaja ditandai server boleh ditampilkan.
                if (message.startsWith("RF: ")) throw new IllegalStateException(message.substring(4));
                if (status == 404) throw new IllegalStateException("API aplikasi belum tersedia. Jalankan migrasi database terlebih dahulu.");
                throw new IllegalStateException("Permintaan ditolak server (HTTP " + status + "). Periksa data dan konfigurasi database.");
            }
            return response.body().isBlank() ? Json.object() : Json.read(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Permintaan dibatalkan.");
        } catch (IOException ex) {
            throw new IllegalStateException("Koneksi gagal atau waktu habis. Hasil penyimpanan mungkin belum diketahui; periksa data sebelum mencoba ulang.",ex);
        }
    }
}

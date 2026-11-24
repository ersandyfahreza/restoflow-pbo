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
        acceptSession(passwordGrant(email, password));
    }

    private JsonNode passwordGrant(String email, String password) {
        return request("/auth/v1/token?grant_type=password",
                Json.object().put("email", email.trim()).put("password", password), "");
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
        if (!function.matches("rf_[a-z_]+")) throw new IllegalArgumentException("Endpoint tidak valid.");
        return request("/rest/v1/rpc/" + function, payload, authenticatedToken());
    }

    public synchronized JsonNode createStaffAccount(JsonNode profile) {
        return request("/functions/v1/provision-staff-account", profile, authenticatedToken());
    }

    public synchronized void changePassword(String currentPassword, String newPassword) {
        JsonNode user = request("GET", "/auth/v1/user", null, authenticatedToken());
        String userId = user.path("id").asText();
        String email = user.path("email").asText();
        if (userId.isBlank() || email.isBlank()) throw new IllegalStateException("Identitas akun tidak dapat diperiksa.");

        // Sign in again so older sessions can change their password without an email nonce.
        JsonNode signedIn = passwordGrant(email, currentPassword);
        String verifiedToken = signedIn.path("access_token").asText();
        if (verifiedToken.isBlank()) throw new IllegalStateException("Sesi verifikasi tidak lengkap.");
        try {
            if (!userId.equals(signedIn.path("user").path("id").asText())) {
                throw new IllegalStateException("Identitas akun berubah. Silakan masuk ulang.");
            }
            request("PUT", "/auth/v1/user", Json.object().put("password", newPassword)
                    .put("current_password", currentPassword), verifiedToken);
        } finally {
            endSessionQuietly(verifiedToken);
        }
        endSessionQuietly(accessToken);
        clearSession();
    }

    private void endSessionQuietly(String token) {
        try { request("/auth/v1/logout?scope=local", Json.object(), token); }
        catch (RuntimeException ignored) { /* Keep the result of the password update. */ }
    }

    private String authenticatedToken() {
        if (accessToken.isBlank()) throw new IllegalStateException("Silakan masuk terlebih dahulu.");
        if (!Instant.now().isBefore(expiresAt)) {
            try { acceptSession(request("/auth/v1/token?grant_type=refresh_token",
                    Json.object().put("refresh_token", refreshToken), "")); }
            catch (RuntimeException ex) { clearSession(); throw new IllegalStateException("Sesi berakhir. Silakan masuk ulang."); }
        }
        return accessToken;
    }

    public synchronized void logout() {
        try { if (!accessToken.isBlank()) request("/auth/v1/logout?scope=local", Json.object(), accessToken); }
        finally { clearSession(); }
    }

    public synchronized void clearSession() {
        accessToken = ""; refreshToken = ""; expiresAt = Instant.EPOCH;
    }

    private JsonNode request(String path, JsonNode body, String token) {
        return request("POST", path, body, token);
    }

    private JsonNode request(String method, String path, JsonNode body, String token) {
        String base = config.url().replaceAll("/+$", "");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                .header("apikey", config.publishableKey()).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(Json.write(body)));
        if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                JsonNode error;
                try { error = Json.read(response.body()); } catch (RuntimeException ex) { error = Json.object(); }
                String code = error.path("error_code").asText(error.path("code").asText());
                if (code.equals("invalid_credentials")) throw new IllegalStateException("Email atau kata sandi salah.");
                if (path.equals("/auth/v1/user")) {
                    if (code.equals("weak_password")) throw new IllegalStateException("Kata sandi belum memenuhi aturan Supabase. Gunakan kata sandi yang lebih kuat.");
                    if (code.equals("same_password")) throw new IllegalStateException("Gunakan kata sandi yang berbeda dari kata sandi sebelumnya.");
                    if (code.equals("current_password_mismatch")) throw new IllegalStateException("Kata sandi lama salah.");
                }
                if (status == 401) throw new IllegalStateException("Email/password salah atau sesi tidak berlaku.");
                if (status == 429) throw new IllegalStateException("Terlalu banyak permintaan. Tunggu sebelum mencoba ulang.");
                String message = error.path("message").asText("");
                // Hanya pesan bisnis yang sengaja ditandai server boleh ditampilkan.
                if (message.startsWith("RF: ")) throw new IllegalStateException(message.substring(4));
                if (status == 403) throw new IllegalStateException("Akun tidak memiliki izin untuk tindakan ini.");
                if (status == 404 && path.equals("/functions/v1/provision-staff-account")) {
                    throw new IllegalStateException("Fitur tambah akun belum diaktifkan di Supabase. Hubungi pengelola proyek.");
                }
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

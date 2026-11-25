package id.ac.restoflow.integration;

import com.sun.net.httpserver.HttpServer;
import id.ac.restoflow.config.AppConfig;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseClientTest {
    @Test void changePasswordVerifiesCurrentCredentialsAndKeepsUpdatesOnTheSameAccount() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        record Call(String method, String path, String authorization, com.fasterxml.jackson.databind.JsonNode body) {}
        List<Call> calls = new java.util.concurrent.CopyOnWriteArrayList<>();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            String rawBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var body = rawBody.isBlank() ? Json.object() : Json.read(rawBody);
            calls.add(new Call(exchange.getRequestMethod(), path, exchange.getRequestHeaders().getFirst("Authorization"), body));
            int status = 200;
            String response = "{}";
            if (exchange.getRequestMethod().equals("GET")) {
                response = "{\"id\":\"employee\",\"email\":\"staff@example.invalid\"}";
            } else if (path.contains("grant_type=password")) {
                switch (body.path("password").asText()) {
                    case "wrong-password" -> { status = 400; response = "{\"error_code\":\"invalid_credentials\"}"; }
                    case "other-account-password" -> response = "{\"access_token\":\"other-token\",\"user\":{\"id\":\"other-id\"}}";
                    default -> response = "{\"access_token\":\"verified-token\",\"refresh_token\":\"refresh\",\"expires_in\":3600,\"user\":{\"id\":\"employee\"}}";
                }
                if (calls.size() == 1) response = "{\"access_token\":\"original-token\",\"refresh_token\":\"refresh\",\"expires_in\":3600}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var auth = new SupabaseClient(new AppConfig("http://127.0.0.1:" + server.getAddress().getPort(), "sb_publishable_test"), HttpClient.newHttpClient());
            assertThrows(IllegalStateException.class, () -> auth.changePassword("old-password", "new-password"));
            assertTrue(calls.isEmpty());
            auth.login("staff@example.invalid", "old-password");
            assertThrows(IllegalStateException.class, () -> auth.changePassword("wrong-password", "new-password"));
            assertThrows(IllegalStateException.class, () -> auth.changePassword("other-account-password", "new-password"));
            assertTrue(calls.stream().noneMatch(call -> call.method().equals("PUT")));
            assertEquals("Bearer other-token", calls.get(calls.size() - 1).authorization());
            auth.changePassword("old-password", "new-password");
            assertThrows(IllegalStateException.class, () -> auth.rpc("rf_snapshot", Json.object()));
            Call update = calls.stream().filter(call -> call.method().equals("PUT")).findFirst().orElseThrow();
            assertEquals("/auth/v1/user", update.path());
            assertEquals("Bearer verified-token", update.authorization());
            assertEquals("new-password", update.body().path("password").asText());
            assertEquals("old-password", update.body().path("current_password").asText());
            assertEquals(List.of("Bearer other-token", "Bearer verified-token", "Bearer original-token"),
                    calls.stream().filter(call -> call.path().contains("/logout")).map(Call::authorization).toList());
            assertEquals("Bearer original-token", calls.get(1).authorization());
            assertEquals("staff@example.invalid", calls.get(2).body().path("email").asText());
        } finally { server.stop(0); }
    }
    @Test void loginRefreshAuthenticatedRpcAndLogout() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger refreshes=new AtomicInteger();AtomicInteger writes=new AtomicInteger();
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().toString();String response;
            assertEquals("sb_publishable_test",exchange.getRequestHeaders().getFirst("apikey"));
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            if(path.contains("grant_type=password")){assertTrue(body.contains("email"));response="{\"access_token\":\"first\",\"refresh_token\":\"refresh\",\"expires_in\":0}";}
            else if(path.contains("grant_type=refresh_token")){refreshes.incrementAndGet();assertTrue(body.contains("refresh"));response="{\"access_token\":\"second\",\"refresh_token\":\"newrefresh\",\"expires_in\":3600}";}
            else {assertEquals("Bearer second",exchange.getRequestHeaders().getFirst("Authorization"));writes.incrementAndGet();response="{\"ok\":true}";}
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try{
            SupabaseClient client=new SupabaseClient(new AppConfig("http://127.0.0.1:"+server.getAddress().getPort(),"sb_publishable_test"),HttpClient.newHttpClient());
            client.login("test@example.invalid","test-password");assertTrue(client.rpc("rf_snapshot",Json.object()).path("ok").asBoolean());
            assertTrue(client.createStaffAccount(Json.object().put("role", "kitchen")).path("ok").asBoolean());
            assertEquals(1,refreshes.get());client.logout();assertEquals(3,writes.get());assertThrows(IllegalStateException.class,()->client.rpc("rf_snapshot",Json.object()));
        }finally{server.stop(0);}
    }
    @Test void createStaffAccountFallsBackToSignupAndSaveProfileWhenEdgeFunctionUnavailable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> paths = new java.util.concurrent.CopyOnWriteArrayList<>();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            paths.add(path);
            int status = 200;
            String response = "{}";
            if (path.contains("grant_type=password")) {
                response = "{\"access_token\":\"admin-token\",\"refresh_token\":\"admin-refresh\",\"expires_in\":3600}";
            } else if (path.equals("/functions/v1/provision-staff-account")) {
                status = 404;
                response = "{\"message\":\"Not Found\"}";
            } else if (path.equals("/auth/v1/signup")) {
                assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
                response = "{\"access_token\":\"new-user-token\",\"user\":{\"id\":\"11111111-1111-1111-1111-111111111111\",\"identities\":[{\"id\":\"id1\"}]}}";
            } else if (path.equals("/rest/v1/rpc/rf_execute")) {
                assertEquals("Bearer admin-token", exchange.getRequestHeaders().getFirst("Authorization"));
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(body.contains("\"save_profile\""));
                assertTrue(body.contains("\"11111111-1111-1111-1111-111111111111\""));
                response = "{\"id\":\"11111111-1111-1111-1111-111111111111\"}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new SupabaseClient(new AppConfig("http://127.0.0.1:" + server.getAddress().getPort(), "sb_publishable_test"), HttpClient.newHttpClient());
            client.login("admin@example.invalid", "admin-password");
            var created = client.createStaffAccount(Json.object()
                    .put("email", "newstaff@example.invalid")
                    .put("name", "Staf Baru")
                    .put("role", "kitchen")
                    .put("staff_id", 1)
                    .put("password", "InitialPass123!"));
            assertEquals("11111111-1111-1111-1111-111111111111", created.path("id").asText());
            assertTrue(paths.contains("/auth/v1/signup"));
            assertTrue(paths.contains("/rest/v1/rpc/rf_execute"));
        } finally {
            server.stop(0);
        }
    }
    @Test void backendErrorsDoNotLeakTokensOrSql() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{byte[] bytes="{\"message\":\"private SQL and secret token\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(500,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
        try{
            var client=new SupabaseClient(new AppConfig("http://127.0.0.1:"+server.getAddress().getPort(),"sb_publishable_test"),HttpClient.newHttpClient());
            var ex=assertThrows(IllegalStateException.class,()->client.login("a","b"));assertFalse(ex.getMessage().contains("private SQL"));
        }finally{server.stop(0);}
    }
}

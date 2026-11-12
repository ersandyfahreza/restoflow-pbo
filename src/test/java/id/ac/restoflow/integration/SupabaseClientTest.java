package id.ac.restoflow.integration;

import com.sun.net.httpserver.HttpServer;
import id.ac.restoflow.config.AppConfig;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseClientTest {
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
            assertEquals(1,refreshes.get());client.logout();assertEquals(2,writes.get());assertThrows(IllegalStateException.class,()->client.rpc("rf_snapshot",Json.object()));
        }finally{server.stop(0);}
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

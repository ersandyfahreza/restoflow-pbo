package id.ac.restoflow.repository;

import com.fasterxml.jackson.databind.JsonNode;
import id.ac.restoflow.integration.Json;
import id.ac.restoflow.integration.SupabaseClient;
import java.time.LocalDate;

public final class SupabaseRepository implements RestaurantRepository {
    private final SupabaseClient client;
    public SupabaseRepository(SupabaseClient client) { this.client = client; }
    public JsonNode snapshot(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || from.plusDays(366).isBefore(to))
            throw new IllegalArgumentException("Pilih rentang tanggal berurutan, maksimal 366 hari.");
        return client.rpc("rf_snapshot", Json.object().put("p_from", from.toString()).put("p_to", to.toString()));
    }
    public JsonNode execute(String action, JsonNode payload) {
        return client.rpc("rf_execute", Json.object().put("p_action", action).set("p", payload));
    }
}

package id.ac.restoflow.repository;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;

public interface RestaurantRepository {
    JsonNode snapshot(LocalDate from, LocalDate to);
    JsonNode execute(String action, JsonNode payload);
    JsonNode createStaffAccount(JsonNode profile);
    void changePassword(String currentPassword, String newPassword);
}

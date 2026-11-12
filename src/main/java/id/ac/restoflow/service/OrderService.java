package id.ac.restoflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.ac.restoflow.domain.Cart;
import id.ac.restoflow.integration.Json;
import id.ac.restoflow.repository.RestaurantRepository;
import java.util.UUID;

public final class OrderService {
    private final RestaurantRepository repository;
    public OrderService(RestaurantRepository repository) { this.repository = repository; }

    public JsonNode create(Cart cart, UUID requestId, String type, Integer tableId,
                           String notes, String address, Integer kitchen, String waiter) {
        return repository.execute("create_order",prepare(cart,requestId,type,tableId,notes,address,kitchen,waiter));
    }
    public static ObjectNode prepare(Cart cart, UUID requestId, String type, Integer tableId,
                           String notes, String address, Integer kitchen, String waiter) {
        if (cart.isEmpty()) throw new IllegalArgumentException("Keranjang masih kosong.");
        if (!java.util.Set.of("dine_in", "takeaway", "delivery").contains(type))
            throw new IllegalArgumentException("Jenis pesanan tidak valid.");
        if ("dine_in".equals(type) && tableId == null) throw new IllegalArgumentException("Pilih meja untuk makan di tempat.");
        if ("delivery".equals(type) && address.isBlank()) throw new IllegalArgumentException("Alamat pengiriman wajib diisi.");
        ObjectNode data = Json.object().put("request_id", requestId.toString()).put("type", type)
                .put("notes", notes).put("address", address).put("waiter", waiter);
        if (tableId != null) data.put("table_id", tableId);
        if (kitchen != null) data.put("kitchen_id", kitchen);
        var lines = data.putArray("items");
        for (Cart.Item item : cart.items()) lines.addObject().put("menu_id", item.menuId()).put("quantity", item.quantity());
        // Harga tidak dikirim sebagai sumber kebenaran; database membaca dan menguncinya.
        return data;
    }
}

package id.ac.restoflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.ac.restoflow.domain.Cart;
import id.ac.restoflow.integration.Json;
import id.ac.restoflow.repository.RestaurantRepository;
import java.util.UUID;

public final class OrderService {
    /** Bantuan UI saja; otorisasi dan state terakhir tetap diperiksa server. */
    public static String statusBlockReason(JsonNode order,String role,String next){
        if(order==null)return "Pilih satu pesanan terlebih dahulu.";
        String current=order.path("status").asText();
        boolean transition=switch(current){
            case "Menunggu" -> java.util.Set.of("Diproses","Dibatalkan").contains(next);
            case "Diproses" -> java.util.Set.of("Siap","Dibatalkan").contains(next);
            case "Siap" -> java.util.Set.of("Selesai","Dibatalkan").contains(next);
            default -> false;
        };
        if(!transition)return "Status saat ini "+current+". Ikuti urutan Menunggu → Diproses → Siap → Selesai.";
        if(!java.util.Set.of("admin","cashier","kitchen","waiter").contains(role)
            ||role.equals("kitchen")&&!java.util.Set.of("Diproses","Siap").contains(next)
            ||role.equals("waiter")&&!next.equals("Selesai"))return "Peran Anda tidak dapat melakukan tindakan ini.";
        boolean paid="Lunas".equals(order.path("payment_status").asText());
        if(next.equals("Dibatalkan")&&paid)return "Pesanan lunas tidak dapat dibatalkan. Refund belum didukung.";
        if(next.equals("Selesai")&&!paid)return "Selesaikan pembayaran terlebih dahulu.";
        String delivery=order.path("delivery_status").asText("");
        if(next.equals("Selesai")&&"delivery".equals(order.path("type").asText())&&!delivery.isBlank()&&!delivery.equals("Terkirim"))return "Konfirmasi pengiriman Terkirim terlebih dahulu.";
        return "";
    }
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

package id.ac.restoflow.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keranjang mengenkapsulasi jumlah dan perhitungan rupiah tanpa floating point. */
public final class Cart {
    public record Item(int menuId, String name, long price, int quantity) {
        public long subtotal() { return Math.multiplyExact(price, quantity); }
    }
    private final Map<Integer, Item> items = new LinkedHashMap<>();
    public void add(int id, String name, long price, int quantity) {
        if (id <= 0 || name == null || name.isBlank() || price < 0 || price > 1_000_000_000L)
            throw new IllegalArgumentException("Menu atau harga tidak valid.");
        int combined = Math.addExact(quantity, items.containsKey(id) ? items.get(id).quantity() : 0);
        if (quantity <= 0 || combined > 99) throw new IllegalArgumentException("Jumlah per menu harus 1–99.");
        items.put(id, new Item(id, name, price, combined));
    }
    public void remove(int id) { items.remove(id); }
    public void decrement(int id) {
        Item old=items.get(id);if(old==null)return;
        if(old.quantity()==1)items.remove(id);else items.put(id,new Item(id,old.name(),old.price(),old.quantity()-1));
    }
    public void clear() { items.clear(); }
    public Collection<Item> items() { return java.util.List.copyOf(items.values()); }
    public long total() { return items.values().stream().mapToLong(Item::subtotal).reduce(0, Math::addExact); }
    public boolean isEmpty() { return items.isEmpty(); }
}

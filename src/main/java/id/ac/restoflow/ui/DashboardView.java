package id.ac.restoflow.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Modul tampilan Dashboard ringkasan metrik dan antrean aktif. */
public final class DashboardView implements PageView {
    private final VBox root;

    public DashboardView(JsonNode data, Function<List<JsonNode>, Ui.DataTable> orderTableFactory, Consumer<List<JsonNode>> onOrderDoubleClick) {
        JsonNode s = data.path("stats");
        FlowPane metrics = new FlowPane(12, 12,
                metric("Pendapatan lunas", Ui.money(s.path("revenue").asLong())),
                metric("Periode sebelumnya", Ui.money(s.path("previous_revenue").asLong())),
                metric("Total pesanan", "" + s.path("orders").asLong()),
                metric("Pesanan aktif", "" + s.path("active").asLong()),
                metric("Belum lunas", "" + s.path("pending").asLong()));
        metrics.getStyleClass().add("summary-strip");

        List<JsonNode> activeRows = Ui.rows(data, "orders").stream()
                .filter(n -> !Set.of("Selesai", "Dibatalkan").contains(n.path("status").asText()))
                .toList();
        Ui.DataTable active = orderTableFactory.apply(activeRows);
        active.onRowDoubleClick(row -> onOrderDoubleClick.accept(List.of(row)));

        Map<String, Long> sellers = new LinkedHashMap<>();
        for (JsonNode order : Ui.rows(data, "orders")) {
            if (!"Dibatalkan".equals(order.path("status").asText())) {
                order.path("items").forEach(i -> sellers.merge(i.path("name").asText(), i.path("quantity").asLong(), Long::sum));
            }
        }
        String top = sellers.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .reduce((a, b) -> a + " · " + b)
                .orElse("Belum ada penjualan pada rentang ini.");

        Label bestSellers = Ui.label("Terlaris: " + top);
        bestSellers.getStyleClass().add("muted");

        root = Ui.page(Ui.heading("Dashboard"), metrics, Ui.heading("Antrean aktif"), active.node, bestSellers);
        VBox.setVgrow(active.node, Priority.ALWAYS);
    }

    private static Node metric(String title, String value) {
        Label caption = Ui.label(title);
        caption.getStyleClass().add("muted");
        Label amount = Ui.heading(value);
        amount.getStyleClass().add("metric-value");
        VBox v = new VBox(5, caption, amount);
        v.getStyleClass().add("metric");
        v.setMinWidth(150);
        return v;
    }

    @Override
    public Node node() {
        return root;
    }
}

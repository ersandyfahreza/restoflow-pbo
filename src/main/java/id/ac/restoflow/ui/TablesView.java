package id.ac.restoflow.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.ac.restoflow.integration.Json;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Modul tampilan Denah Meja dengan kode warna status (Tersedia, Sudah terisi, Diarsipkan)
 * dan dialog Menu Meja yang terintegrasi dengan pintasan pesanan aktif.
 */
public final class TablesView implements PageView {
    public interface TableCallbacks {
        boolean isAdmin();
        boolean canManageOrders();
        boolean canAcceptPayment();
        void openNewTableDialog();
        void saveTable(ObjectNode payload, Runnable onSuccess, Consumer<Throwable> onError);
        void startOrderAtTable(String tableId);
        void showOrderDetail(JsonNode order);
        void editWaitingOrder(JsonNode order);
        void recordPayment(JsonNode order);
        void completeReadyOrder(JsonNode order);
    }

    private final Stage stage;
    private final JsonNode data;
    private final TableCallbacks callbacks;
    private final VBox root;

    public TablesView(Stage stage, JsonNode data, TableCallbacks callbacks) {
        this.stage = stage;
        this.data = data;
        this.callbacks = callbacks;

        TextField search = new TextField();
        search.setId("table-search");
        search.getStyleClass().add("table-search");
        search.setPromptText("Cari nomor meja, kode QR, atau status (Tersedia / Sudah terisi / Diarsipkan)...");
        Button clearSearch = Ui.button("×", search::clear);
        clearSearch.getStyleClass().add("search-clear-button");
        clearSearch.setTooltip(new Tooltip("Kosongkan pencarian (Esc)"));
        clearSearch.setVisible(false);
        clearSearch.setManaged(false);
        search.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasText = newVal != null && !newVal.isEmpty();
            clearSearch.setVisible(hasText);
            clearSearch.setManaged(hasText);
        });
        search.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                search.clear();
                e.consume();
            }
        });
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox searchBar = new HBox(6, search, clearSearch);
        searchBar.setAlignment(Pos.CENTER_LEFT);

        Label count = Ui.label("");
        count.getStyleClass().add("table-count");

        TilePane grid = new TilePane(14, 14);
        grid.setId("table-grid");
        grid.setPadding(new Insets(12, 4, 24, 4));
        grid.setPrefTileWidth(185);

        List<JsonNode> allTables = Ui.rows(data, "tables");
        Runnable renderTiles = () -> {
            String q = search.getText().toLowerCase(Locale.ROOT).trim();
            grid.getChildren().clear();
            int shown = 0;
            int availableCount = 0;
            int occupiedCount = 0;
            int archivedCount = 0;
            for (JsonNode row : allTables) {
                int tableId = row.path("id_customer").asInt();
                boolean active = row.path("is_active").asBoolean();
                JsonNode activeOrder = findActiveOrderForTable(data, tableId);
                boolean occupied = isTableBusy(data, tableId, activeOrder);
                if (!active) archivedCount++;
                else if (occupied) occupiedCount++;
                else availableCount++;

                String status = !active ? "Diarsipkan" : (occupied ? "Sudah terisi" : "Tersedia");
                String orderBadge = active && occupied && activeOrder != null
                        ? " · #" + activeOrder.path("id").asText() + " (" + activeOrder.path("status").asText() + ")"
                        : "";
                String tableName = Ui.text(row, "nomor_meja");
                String qrCode = Ui.text(row, "qr_meja");
                String searchable = (tableName + " " + qrCode + " " + status + " " + orderBadge).toLowerCase(Locale.ROOT);
                if (!q.isEmpty() && !searchable.contains(q)) continue;
                shown++;

                Button tile = Ui.button(tableName + "\n" + status + orderBadge + "\n" + qrCode, () -> openTableMenu(row, activeOrder));
                tile.setId("table-tile-" + row.path("id_customer").asText());
                tile.setPrefSize(185, 108);
                tile.getStyleClass().add(!active ? "table-tile-archived" : (occupied ? "table-tile-occupied" : "table-tile-available"));
                grid.getChildren().add(tile);
            }
            count.setText(shown + " dari " + allTables.size() + " meja ditampilkan · Tersedia: " + availableCount
                    + " · Sudah terisi: " + occupiedCount + " · Diarsipkan: " + archivedCount);
        };
        search.textProperty().addListener((obs, oldVal, newVal) -> renderTiles.run());
        renderTiles.run();

        Button addTableBtn = Ui.button("Tambah meja", callbacks::openNewTableDialog);
        addTableBtn.setVisible(callbacks.isAdmin());
        addTableBtn.setManaged(addTableBtn.isVisible());

        FlowPane bar = new FlowPane(12, 8, addTableBtn);
        bar.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = Ui.scroll(grid);
        root = Ui.page(Ui.heading("Denah Meja"), bar, searchBar, count, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
    }

    public static JsonNode findActiveOrderForTable(JsonNode data, int tableId) {
        return Ui.rows(data, "orders").stream()
                .filter(o -> o.path("table_id").asInt(-1) == tableId
                        && !Set.of("Selesai", "Dibatalkan").contains(o.path("status").asText()))
                .findFirst()
                .orElse(null);
    }

    public static boolean isTableBusy(JsonNode data, int tableId) {
        return isTableBusy(data, tableId, findActiveOrderForTable(data, tableId));
    }

    private static boolean isTableBusy(JsonNode data, int tableId, JsonNode activeOrder) {
        return activeOrder != null
                || Ui.rows(data, "tables").stream().anyMatch(t -> t.path("id_customer").asInt() == tableId && t.path("occupied").asBoolean());
    }

    private void openTableMenu(JsonNode row, JsonNode activeOrder) {
        int tableId = row.path("id_customer").asInt();
        boolean isActive = row.path("is_active").asBoolean();
        boolean occupied = isTableBusy(data, tableId, activeOrder);
        String activeDescription = !isActive ? "Nonaktif (Diarsipkan)"
                : (occupied ? "Aktif · Sudah terisi pesanan aktif" : "Aktif · Tersedia untuk pesanan baru");

        Dialog<Void> dialog = new Dialog<>();
        Ui.styleDialog(dialog);
        dialog.initOwner(stage);
        dialog.setTitle("Menu Meja - " + Ui.text(row, "nomor_meja"));
        dialog.getDialogPane().setPrefWidth(580);
        dialog.setOnCloseRequest(event -> {
            if (dialog.getDialogPane().isDisabled()) event.consume();
        });

        TextField name = new TextField(row.path("nomor_meja").asText(""));
        name.setId("table-menu-name");
        TextField qr = new TextField(row.path("qr_meja").asText(""));
        qr.setId("table-menu-qr");
        CheckBox activeBox = new CheckBox("Aktif");
        activeBox.setSelected(isActive);
        activeBox.setId("table-menu-active");
        Label statusLabel = Ui.label(activeDescription);
        statusLabel.setId("table-menu-status");

        boolean isAdmin = callbacks.isAdmin();
        name.setEditable(isAdmin);
        qr.setEditable(isAdmin);
        activeBox.setDisable(!isAdmin);

        Button addOrder = Ui.button("Tambah pesanan", () -> {
            if (!activeBox.isSelected() || !isActive) {
                Ui.error("Meja tidak aktif.");
                return;
            }
            if (occupied) {
                Ui.error("Meja ini sudah terisi pesanan aktif.");
                return;
            }
            dialog.close();
            callbacks.startOrderAtTable(row.path("id_customer").asText());
        });
        addOrder.setId("table-menu-add-order");
        addOrder.getStyleClass().add("primary");
        addOrder.setDisable(!isActive || occupied);
        if (!isActive) addOrder.setTooltip(new Tooltip("Meja tidak aktif."));
        else if (occupied) addOrder.setTooltip(new Tooltip("Meja sudah terisi pesanan aktif."));

        GridPane f = Ui.form();
        Ui.field(f, 0, "Nama meja", name);
        Ui.field(f, 1, "Kode QR", qr);
        Ui.field(f, 2, "Keterangan aktif", new VBox(6, statusLabel, activeBox));
        Ui.field(f, 3, "Pemesanan baru", addOrder);

        VBox content = Ui.page(f);
        if (occupied && activeOrder != null) {
            String orderSummary = "Pesanan #" + activeOrder.path("id").asText()
                    + " · Antrean " + Ui.text(activeOrder, "queue")
                    + " · Status: " + Ui.text(activeOrder, "status")
                    + " · Pembayaran: " + Ui.text(activeOrder, "payment_status")
                    + " (" + Ui.money(activeOrder.path("total").asLong()) + ")";
            Label activeOrderHeader = Ui.heading("Pesanan Aktif di Meja Ini");
            Label activeOrderInfo = Ui.label(orderSummary);
            FlowPane shortcuts = new FlowPane(8, 8);
            Button viewDetail = Ui.button("Lihat rincian", () -> {
                dialog.close();
                callbacks.showOrderDetail(activeOrder);
            });
            shortcuts.getChildren().add(viewDetail);

            if (callbacks.canManageOrders() && "Menunggu".equals(activeOrder.path("status").asText())
                    && "Pending".equals(activeOrder.path("payment_status").asText())) {
                Button editOrder = Ui.button("Tambah / Edit item (POS)", () -> {
                    dialog.close();
                    callbacks.editWaitingOrder(activeOrder);
                });
                shortcuts.getChildren().add(editOrder);
            }
            if (callbacks.canAcceptPayment() && "Pending".equals(activeOrder.path("payment_status").asText())
                    && !"Dibatalkan".equals(activeOrder.path("status").asText())) {
                Button payOrder = Ui.button("Catat pembayaran", () -> {
                    dialog.close();
                    callbacks.recordPayment(activeOrder);
                });
                payOrder.getStyleClass().add("primary");
                shortcuts.getChildren().add(payOrder);
            }
            if (callbacks.canManageOrders() && "Siap".equals(activeOrder.path("status").asText())) {
                Button finishOrder = Ui.button("Selesaikan pesanan", () -> {
                    dialog.close();
                    callbacks.completeReadyOrder(activeOrder);
                });
                finishOrder.getStyleClass().add("primary");
                shortcuts.getChildren().add(finishOrder);
            }
            VBox activeBoxCard = new VBox(8, new Separator(), activeOrderHeader, activeOrderInfo, shortcuts);
            content.getChildren().add(activeBoxCard);
        }

        dialog.getDialogPane().setContent(content);
        if (isAdmin) {
            dialog.getDialogPane().getButtonTypes().addAll(Ui.SAVE, ButtonType.CLOSE);
            dialog.getDialogPane().lookupButton(Ui.SAVE).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                event.consume();
                String trimmedName = name.getText().trim();
                String trimmedQr = qr.getText().trim();
                if (trimmedName.isBlank() || trimmedQr.isBlank()) {
                    Ui.error("Nama meja dan kode QR wajib diisi.");
                    return;
                }
                ObjectNode p = Json.object()
                        .put("id", tableId)
                        .put("name", trimmedName)
                        .put("qr", trimmedQr)
                        .put("active", activeBox.isSelected());
                dialog.getDialogPane().setDisable(true);
                callbacks.saveTable(p, () -> {
                    dialog.getDialogPane().setDisable(false);
                    dialog.close();
                }, error -> {
                    dialog.getDialogPane().setDisable(false);
                    Ui.error(error.getMessage());
                });
            });
        } else {
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        }
        dialog.showAndWait();
    }

    @Override
    public Node node() {
        return root;
    }
}

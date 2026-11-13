package id.ac.restoflow.ui;

import com.fasterxml.jackson.databind.JsonNode;
import id.ac.restoflow.domain.Cart;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import java.util.UUID;
import java.util.function.Consumer;

public final class PosView {
    public final Cart cart=new Cart();
    public UUID requestId=UUID.randomUUID();
    public Integer editingId;
    public final ComboBox<String> type=new ComboBox<>(FXCollections.observableArrayList("dine_in","takeaway","delivery"));
    public final TextArea notes=new TextArea();
    public final TextArea address=new TextArea();
    public ComboBox<Ui.Choice> table,kitchen,waiter;
    private final TableView<Cart.Item> cartTable=new TableView<>();
    private final Label total=Ui.heading("");
    private final VBox node;

    public PosView(JsonNode data,Consumer<PosView> save) {
        type.setValue("dine_in");notes.setPrefRowCount(2);address.setPrefRowCount(2);
        table=Ui.choices(data,"tables","id_customer",n->Ui.text(n,"nomor_meja"),true);
        kitchen=Ui.choices(data,"staff","id_staff",n->Ui.staff(n).displayLabel(),true);
        kitchen.getItems().removeIf(c->Ui.rows(data,"kitchen").stream().noneMatch(k->k.path("id_staff").asText().equals(c.id())));
        waiter=Ui.choices(data,"waiters","id_pelayan",n->n.path("nama_depan").asText()+" "+n.path("nama_belakang").asText(),true);
        table.disableProperty().bind(type.valueProperty().isNotEqualTo("dine_in"));
        address.disableProperty().bind(type.valueProperty().isNotEqualTo("delivery"));
        Ui.DataTable menu=new Ui.DataTable(Ui.rows(data,"menu").stream().filter(m->m.path("is_active").asBoolean()).toList(),
            Ui.col("MENU","nama_menu"),new Ui.Column("HARGA",n->Ui.money(n.path("harga_satuan").asLong())),Ui.col("KATEGORI","category"));
        menu.search.setPromptText("Cari nama menu atau kategori...");
        ComboBox<String> category=new ComboBox<>();category.getItems().add("Semua kategori");
        Ui.rows(data,"menu").stream().map(n->n.path("category").asText()).distinct().sorted().forEach(category.getItems()::add);
        category.setValue("Semua kategori");category.setOnAction(e->menu.search.setText("Semua kategori".equals(category.getValue())?"":category.getValue()));
        Spinner<Integer> qty=new Spinner<>(1,99,1);qty.setPrefWidth(90);
        Button add=Ui.button("Tambah ke keranjang",()->{
            try { JsonNode m=menu.selected();cart.add(m.path("id_menu").asInt(),m.path("nama_menu").asText(),m.path("harga_satuan").asLong(),qty.getValue());refreshCart(); }
            catch(RuntimeException ex){Ui.error(ex.getMessage());}
        });
        category.setMaxWidth(Double.MAX_VALUE);
        add.getStyleClass().add("primary");
        VBox left=new VBox(8,Ui.heading("PILIH MENU"),
            Ui.label("Harga akhir diperiksa kembali oleh server. Dapur dan pelayan dipilih berpasangan atau dikosongkan."),
            category,menu.node,new HBox(8,qty,add));
        left.getStyleClass().add("pos-panel");VBox.setVgrow(menu.node,Priority.ALWAYS);
        for(String title:new String[]{"MENU","HARGA","JUMLAH","SUBTOTAL"}){
            TableColumn<Cart.Item,String> c=new TableColumn<>(title);c.setMinWidth(title.equals("MENU")?120:80);c.setPrefWidth(title.equals("MENU")?180:110);
            c.setCellValueFactory(v->new ReadOnlyStringWrapper(switch(title){case "MENU"->v.getValue().name();case "HARGA"->Ui.money(v.getValue().price());case "JUMLAH"->""+v.getValue().quantity();default->Ui.money(v.getValue().subtotal());}));
            cartTable.getColumns().add(c);
        }
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        cartTable.setPlaceholder(Ui.label("Keranjang masih kosong."));cartTable.setPrefHeight(190);cartTable.setMinHeight(120);
        Button remove=Ui.button("Hapus item",()->{var item=cartTable.getSelectionModel().getSelectedItem();if(item!=null){cart.remove(item.menuId());refreshCart();}});
        GridPane fields=Ui.form();Ui.field(fields,0,"Jenis pesanan",type);Ui.field(fields,1,"Meja",table);
        Ui.field(fields,2,"Catatan",notes);Ui.field(fields,3,"Alamat delivery",address);
        Ui.field(fields,4,"Staf dapur (opsional)",kitchen);Ui.field(fields,5,"Pelayan (opsional)",waiter);
        Button submit=Ui.button("Simpan pesanan",()->save.accept(this));submit.getStyleClass().add("primary");
        Button clear=Ui.button("Pesanan baru",()->{if(cart.isEmpty()||Ui.confirm("Kosongkan keranjang? Jika penyimpanan sebelumnya mengalami timeout, periksa daftar pesanan dahulu."))reset();});
        Button decrease=Ui.button("−1",()->{var item=cartTable.getSelectionModel().getSelectedItem();if(item!=null){cart.decrement(item.menuId());refreshCart();}});
        Button increase=Ui.button("+1",()->{var item=cartTable.getSelectionModel().getSelectedItem();if(item!=null)try{cart.add(item.menuId(),item.name(),item.price(),1);refreshCart();}catch(RuntimeException ex){Ui.error(ex.getMessage());}});
        HBox actions=new HBox(8,increase,decrease,remove);
        TitledPane details=new TitledPane("Detail pesanan",fields);details.setExpanded(true);
        VBox orderContent=new VBox(12,cartTable,actions,details);orderContent.setPadding(new javafx.geometry.Insets(0,4,8,0));
        ScrollPane orderScroll=Ui.scroll(orderContent);orderScroll.setFitToWidth(true);orderScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox orderHeader=new VBox(4,Ui.heading("PEMESANAN"),Ui.label("Total pesanan yang akan dipesan oleh pelanggan"));
        BorderPane right=new BorderPane();right.setTop(orderHeader);right.setCenter(orderScroll);
        Region footerSpacer=new Region();HBox.setHgrow(footerSpacer,Priority.ALWAYS);
        total.getStyleClass().add("order-total");
        HBox footer=new HBox(10,total,footerSpacer,submit,clear);footer.getStyleClass().add("order-footer");right.setBottom(footer);
        right.getStyleClass().add("pos-panel");
        HBox panels=new HBox(24,left,right);HBox.setHgrow(left,Priority.ALWAYS);HBox.setHgrow(right,Priority.ALWAYS);
        left.setMinWidth(0);right.setMinWidth(0);left.setMaxWidth(Double.MAX_VALUE);right.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(menu.node,Priority.ALWAYS);
        panels.getStyleClass().add("pos-layout");
        node=Ui.page(panels);
        VBox.setVgrow(panels,Priority.ALWAYS);
        refreshCart();
    }
    public Node node(){return node;}
    public void refreshCart(){cartTable.setItems(FXCollections.observableArrayList(cart.items()));total.setText("Estimasi "+Ui.money(cart.total()));}
    public void reset(){cart.clear();editingId=null;requestId=UUID.randomUUID();notes.clear();address.clear();table.setValue(null);kitchen.setValue(null);waiter.setValue(null);type.setValue("dine_in");refreshCart();}
    public void edit(JsonNode order){
        reset();editingId=order.path("id").asInt();type.setValue(order.path("type").asText());notes.setText(order.path("notes").asText(""));address.setText(order.path("address").asText(""));
        Ui.select(table,order.path("table_id").asText());
        for(JsonNode item:order.path("items"))cart.add(item.path("menu_id").asInt(),item.path("name").asText(),item.path("price").asLong(),item.path("quantity").asInt());
        if(order.path("assignments").isArray()&&!order.path("assignments").isEmpty()){
            Ui.select(kitchen,order.path("assignments").get(0).path("id_staff").asText());Ui.select(waiter,order.path("assignments").get(0).path("id_pelayan").asText());
        }
        refreshCart();
    }
}

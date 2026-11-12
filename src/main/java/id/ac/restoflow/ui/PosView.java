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
            Ui.col("Menu","nama_menu"),Ui.col("Kategori","category"),new Ui.Column("Harga",n->Ui.money(n.path("harga_satuan").asLong())));
        ComboBox<String> category=new ComboBox<>();category.getItems().add("Semua kategori");
        Ui.rows(data,"menu").stream().map(n->n.path("category").asText()).distinct().sorted().forEach(category.getItems()::add);
        category.setValue("Semua kategori");category.setOnAction(e->menu.search.setText("Semua kategori".equals(category.getValue())?"":category.getValue()));
        Spinner<Integer> qty=new Spinner<>(1,99,1);qty.setPrefWidth(90);
        Button add=Ui.button("Tambah ke keranjang",()->{
            try { JsonNode m=menu.selected();cart.add(m.path("id_menu").asInt(),m.path("nama_menu").asText(),m.path("harga_satuan").asLong(),qty.getValue());refreshCart(); }
            catch(RuntimeException ex){Ui.error(ex.getMessage());}
        });
        VBox left=new VBox(12,Ui.heading("Pilih menu"),category,menu.node,new HBox(10,qty,add));VBox.setVgrow(menu.node,Priority.ALWAYS);
        for(String title:new String[]{"Menu","Jumlah","Subtotal"}){
            TableColumn<Cart.Item,String> c=new TableColumn<>(title);c.setPrefWidth(title.equals("Menu")?180:100);
            c.setCellValueFactory(v->new ReadOnlyStringWrapper(switch(title){case "Menu"->v.getValue().name();case "Jumlah"->""+v.getValue().quantity();default->Ui.money(v.getValue().subtotal());}));cartTable.getColumns().add(c);
        }
        cartTable.setPlaceholder(Ui.label("Belum ada item."));cartTable.setPrefHeight(210);
        Button remove=Ui.button("Hapus item",()->{var item=cartTable.getSelectionModel().getSelectedItem();if(item!=null){cart.remove(item.menuId());refreshCart();}});
        GridPane fields=Ui.form();Ui.field(fields,0,"Jenis pesanan",type);Ui.field(fields,1,"Meja",table);
        Ui.field(fields,2,"Catatan",notes);Ui.field(fields,3,"Alamat delivery",address);
        Ui.field(fields,4,"Staf dapur (opsional)",kitchen);Ui.field(fields,5,"Pelayan (opsional)",waiter);
        Button submit=Ui.button("Simpan pesanan",()->save.accept(this));submit.getStyleClass().add("primary");
        Button clear=Ui.button("Pesanan baru",()->{if(cart.isEmpty()||Ui.confirm("Kosongkan keranjang? Jika penyimpanan sebelumnya mengalami timeout, periksa daftar pesanan dahulu."))reset();});
        Button decrease=Ui.button("−1",()->{var item=cartTable.getSelectionModel().getSelectedItem();if(item!=null){cart.decrement(item.menuId());refreshCart();}});
        Button increase=Ui.button("+1",()->{var item=cartTable.getSelectionModel().getSelectedItem();if(item!=null)try{cart.add(item.menuId(),item.name(),item.price(),1);refreshCart();}catch(RuntimeException ex){Ui.error(ex.getMessage());}});
        VBox right=new VBox(12,Ui.heading("Keranjang"),cartTable,new HBox(10,decrease,increase,remove),total,new HBox(12,submit,clear),fields);
        SplitPane split=new SplitPane(left,Ui.scroll(right));split.setDividerPositions(.51);VBox.setVgrow(split,Priority.ALWAYS);
        node=Ui.page(Ui.heading("POS / Pemesanan"),Ui.label("Harga akhir diperiksa kembali oleh server. Dapur dan pelayan dipilih berpasangan atau dikosongkan."),split);
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

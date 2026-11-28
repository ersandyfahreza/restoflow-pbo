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

public final class PosView implements PageView {
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
        type.setConverter(new javafx.util.StringConverter<>(){
            public String toString(String value){return value==null?"":switch(value){case "dine_in"->"Makan di tempat";case "takeaway"->"Bawa pulang";default->"Pengiriman";};}
            public String fromString(String value){throw new UnsupportedOperationException("Pilih jenis pesanan dari daftar.");}
        });
        table=Ui.choices(data,"tables","id_customer",n->Ui.text(n,"nomor_meja")+(isTableOccupied(data,n)?" (Sudah terisi)":" (Tersedia)"),true);
        table.setId("pos-table");
        Label tableOccupancyNotice=Ui.label("");
        tableOccupancyNotice.setId("pos-table-notice");
        tableOccupancyNotice.getStyleClass().add("validation-message");
        tableOccupancyNotice.setVisible(false);
        tableOccupancyNotice.setManaged(false);
        Runnable updateTableNotice=()->{
            boolean dineIn="dine_in".equals(type.getValue());
            Integer selectedTableId=Ui.integerChoice(table);
            boolean occupied=dineIn&&selectedTableId!=null&&isTableOccupiedByOtherOrder(data,selectedTableId,editingId);
            tableOccupancyNotice.setText(occupied?"Keterangan: Meja ini sudah terisi pesanan aktif. Pilih meja lain yang tersedia.":"");
            tableOccupancyNotice.setVisible(occupied);
            tableOccupancyNotice.setManaged(occupied);
        };
        table.valueProperty().addListener((obs,oldVal,newVal)->updateTableNotice.run());
        type.valueProperty().addListener((obs,oldVal,newVal)->updateTableNotice.run());

        Ui.Choice cancelStaffChoice=new Ui.Choice("","— Batal pilih / Kosongkan —");
        kitchen=Ui.choices(data,"staff","id_staff",n->Ui.staff(n).displayLabel(),true);
        kitchen.getItems().removeIf(c->Ui.rows(data,"kitchen").stream().noneMatch(k->k.path("id_staff").asText().equals(c.id())));
        kitchen.getItems().add(0,cancelStaffChoice);
        kitchen.setId("pos-kitchen");
        kitchen.valueProperty().addListener((obs,oldVal,newVal)->{
            if(newVal!=null&&newVal.id().isBlank())javafx.application.Platform.runLater(()->kitchen.setValue(null));
        });

        Ui.Choice cancelWaiterChoice=new Ui.Choice("","— Batal pilih / Kosongkan —");
        waiter=Ui.choices(data,"waiters","id_pelayan",n->n.path("nama_depan").asText()+" "+n.path("nama_belakang").asText(),true);
        waiter.getItems().add(0,cancelWaiterChoice);
        waiter.setId("pos-waiter");
        waiter.valueProperty().addListener((obs,oldVal,newVal)->{
            if(newVal!=null&&newVal.id().isBlank())javafx.application.Platform.runLater(()->waiter.setValue(null));
        });

        table.disableProperty().bind(type.valueProperty().isNotEqualTo("dine_in"));
        address.disableProperty().bind(type.valueProperty().isNotEqualTo("delivery"));
        Ui.DataTable menu=new Ui.DataTable(Ui.rows(data,"menu").stream().filter(m->m.path("is_active").asBoolean()).toList(),
            Ui.col("Menu","nama_menu"),new Ui.Column("Harga",n->Ui.money(n.path("harga_satuan").asLong())),Ui.col("Kategori","category"));
        menu.search.setPromptText("Cari menu...");
        menu.onRowDoubleClick(m->{
            try{
                cart.add(m.path("id_menu").asInt(),m.path("nama_menu").asText(),m.path("harga_satuan").asLong(),1);
                refreshCart();
            }catch(RuntimeException ex){Ui.error(ex.getMessage());}
        });
        ComboBox<String> category=new ComboBox<>();category.getItems().addAll("Semua kategori","Makanan","Minuman");
        Ui.rows(data,"menu").stream().map(n->n.path("category").asText()).filter(c->!c.isBlank()&&!category.getItems().contains(c)).distinct().sorted().forEach(category.getItems()::add);
        category.setValue("Semua kategori");category.setOnAction(e->menu.setRowFilter(row->"Semua kategori".equals(category.getValue())||category.getValue().equals(row.path("category").asText())));
        Spinner<Integer> qty=new Spinner<>(1,99,1);qty.setPrefWidth(90);
        category.setId("pos-category");qty.setId("pos-quantity");
        qty.setAccessibleText("Jumlah menu yang ditambahkan");category.setAccessibleText("Filter kategori menu");
        Button add=Ui.button("Tambah ke keranjang",()->{
            try {
                for(JsonNode m:menu.selectedItems()){
                    cart.add(m.path("id_menu").asInt(),m.path("nama_menu").asText(),m.path("harga_satuan").asLong(),qty.getValue());
                }
                refreshCart();
            }
            catch(RuntimeException ex){Ui.error(ex.getMessage());}
        });
        category.setMaxWidth(Double.MAX_VALUE);
        add.getStyleClass().add("primary");
        add.setId("pos-add");add.disableProperty().bind(menu.table.getSelectionModel().selectedItemProperty().isNull());
        menu.multiSelect.setId("pos-menu-multiselect");
        menu.selectAll.setId("pos-menu-selectall");
        menu.clearSelection.setId("pos-menu-clearselect");
        Label categoryLabel=Ui.label("Kategori");categoryLabel.setLabelFor(category);
        Label qtyLabel=Ui.label("Jumlah");qtyLabel.setLabelFor(qty);
        FlowPane addRow=new FlowPane(8,8,qtyLabel,qty,add);
        VBox left=new VBox(8,Ui.heading("Pilih menu"),categoryLabel,category,menu.node,addRow);
        left.getStyleClass().add("pos-panel");VBox.setVgrow(menu.node,Priority.ALWAYS);
        for(String title:new String[]{"Menu","Harga","Jumlah","Subtotal"}){
            TableColumn<Cart.Item,String> c=new TableColumn<>(title);c.setMinWidth(title.equals("Menu")?100:65);c.setPrefWidth(title.equals("Menu")?180:110);
            c.setCellValueFactory(v->new ReadOnlyStringWrapper(switch(title){case "Menu"->v.getValue().name();case "Harga"->Ui.money(v.getValue().price());case "Jumlah"->""+v.getValue().quantity();default->Ui.money(v.getValue().subtotal());}));
            cartTable.getColumns().add(c);
            if(!title.equals("Menu"))c.setStyle("-fx-alignment: CENTER-RIGHT;");
        }
        ToggleButton cartMultiSelect=new ToggleButton("Pilih banyak");
        cartMultiSelect.setId("pos-cart-multiselect");
        cartMultiSelect.setTooltip(new Tooltip("Aktifkan untuk memilih beberapa item keranjang sekaligus dengan klik."));
        Ui.enableMultiSelect(cartTable,cartMultiSelect);
        Button cartSelectAll=Ui.button("Pilih semua",()->cartTable.getSelectionModel().selectAll());
        cartSelectAll.setId("pos-cart-selectall");
        cartSelectAll.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(cartTable.getItems()));
        Button cartClearSelect=Ui.button("Batal pilih",()->cartTable.getSelectionModel().clearSelection());
        cartClearSelect.setId("pos-cart-clearselect");
        cartClearSelect.disableProperty().bind(cartTable.getSelectionModel().selectedItemProperty().isNull());
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        cartTable.setPlaceholder(Ui.label("Keranjang masih kosong."));cartTable.setPrefHeight(190);cartTable.setMinHeight(120);
        Button remove=Ui.button("Hapus item",()->{
            var selected=new java.util.ArrayList<>(cartTable.getSelectionModel().getSelectedItems());
            if(!selected.isEmpty()){
                for(Cart.Item item:selected)if(item!=null)cart.remove(item.menuId());
                refreshCart();
            }
        });
        cartTable.setId("pos-cart");cartTable.setAccessibleText("Keranjang pesanan");
        VBox tableBox=new VBox(4,table,tableOccupancyNotice);
        GridPane fields=Ui.form();Ui.field(fields,0,"Jenis pesanan",type);Ui.field(fields,1,"Meja",tableBox);
        Ui.field(fields,2,"Catatan",notes);Ui.field(fields,3,"Alamat delivery",address);
        Ui.field(fields,4,"Staf dapur (opsional)",kitchen);Ui.field(fields,5,"Pelayan (opsional)",waiter);
        Button submit=Ui.button("Simpan pesanan",()->save.accept(this));submit.getStyleClass().add("primary");
        submit.setId("pos-save");submit.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(cartTable.getItems()));
        Button clear=Ui.button("Pesanan baru",()->{if(cart.isEmpty()||Ui.confirm("Kosongkan keranjang? Jika penyimpanan sebelumnya mengalami timeout, periksa daftar pesanan dahulu."))reset();});
        Button decrease=Ui.button("−1",()->{
            var selected=new java.util.ArrayList<>(cartTable.getSelectionModel().getSelectedItems());
            if(!selected.isEmpty()){
                java.util.Set<Integer> ids=new java.util.HashSet<>();
                for(Cart.Item item:selected)if(item!=null){ids.add(item.menuId());cart.decrement(item.menuId());}
                refreshCart();
                reselectCartItems(ids);
            }
        });
        Button increase=Ui.button("+1",()->{
            var selected=new java.util.ArrayList<>(cartTable.getSelectionModel().getSelectedItems());
            if(!selected.isEmpty())try{
                java.util.Set<Integer> ids=new java.util.HashSet<>();
                for(Cart.Item item:selected)if(item!=null){ids.add(item.menuId());cart.add(item.menuId(),item.name(),item.price(),1);}
                refreshCart();
                reselectCartItems(ids);
            }catch(RuntimeException ex){Ui.error(ex.getMessage());}
        });
        increase.setId("pos-increase");decrease.setId("pos-decrease");remove.setId("pos-remove");clear.setId("pos-new");
        increase.setAccessibleText("Tambah jumlah item terpilih");decrease.setAccessibleText("Kurangi jumlah item terpilih");
        for(Button button:new Button[]{increase,decrease,remove})button.disableProperty().bind(cartTable.getSelectionModel().selectedItemProperty().isNull());
        FlowPane actions=new FlowPane(8,8,increase,decrease,remove,cartMultiSelect,cartSelectAll,cartClearSelect);
        TitledPane details=new TitledPane("Detail pesanan",fields);details.setExpanded(true);
        details.setId("pos-details");
        VBox orderContent=new VBox(12,cartTable,actions,details);orderContent.setPadding(new javafx.geometry.Insets(0,4,8,0));
        ScrollPane orderScroll=Ui.scroll(orderContent);orderScroll.setFitToWidth(true);orderScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox orderHeader=new VBox(4,Ui.heading("Pesanan pelanggan"));
        BorderPane right=new BorderPane();right.setTop(orderHeader);right.setCenter(orderScroll);
        total.getStyleClass().add("order-total");
        VBox footer=new VBox(8,total,new FlowPane(8,8,submit,clear));footer.getStyleClass().add("order-footer");right.setBottom(footer);
        right.getStyleClass().add("pos-panel");
        HBox panels=new HBox(24,left,right);HBox.setHgrow(left,Priority.ALWAYS);HBox.setHgrow(right,Priority.ALWAYS);
        left.setMinWidth(0);right.setMinWidth(0);left.setMaxWidth(Double.MAX_VALUE);right.setMaxWidth(Double.MAX_VALUE);
        left.prefWidthProperty().bind(panels.widthProperty().subtract(24).multiply(.46));right.prefWidthProperty().bind(panels.widthProperty().subtract(24).multiply(.54));
        HBox.setHgrow(menu.node,Priority.ALWAYS);
        panels.getStyleClass().add("pos-layout");
        node=Ui.page(panels);
        VBox.setVgrow(panels,Priority.ALWAYS);
        refreshCart();
    }
    private static boolean isTableOccupied(JsonNode data,JsonNode tableRow){
        int id=tableRow.path("id_customer").asInt();
        return tableRow.path("occupied").asBoolean()||isTableOccupiedByOtherOrder(data,id,null);
    }
    private static boolean isTableOccupiedByOtherOrder(JsonNode data,int tableId,Integer editingOrderId){
        boolean occupiedFlag=Ui.rows(data,"tables").stream()
            .anyMatch(t->t.path("id_customer").asInt()==tableId&&t.path("occupied").asBoolean());
        boolean activeOrderInSnapshot=Ui.rows(data,"orders").stream()
            .anyMatch(o->o.path("table_id").asInt(-1)==tableId
                &&(editingOrderId==null||o.path("id").asInt()!=editingOrderId)
                &&!java.util.Set.of("Selesai","Dibatalkan").contains(o.path("status").asText()));
        if(editingOrderId!=null&&Ui.rows(data,"orders").stream().anyMatch(o->o.path("id").asInt()==editingOrderId&&o.path("table_id").asInt(-1)==tableId)){
            return activeOrderInSnapshot;
        }
        return occupiedFlag||activeOrderInSnapshot;
    }
    public Node node(){return node;}
    public void refreshCart(){cartTable.getItems().setAll(cart.items());total.setText("Estimasi "+Ui.money(cart.total()));}
    private void reselectCartItems(java.util.Set<Integer> menuIds){
        var sm=cartTable.getSelectionModel();
        sm.clearSelection();
        for(int i=0;i<cartTable.getItems().size();i++){
            if(menuIds.contains(cartTable.getItems().get(i).menuId()))sm.select(i);
        }
    }
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

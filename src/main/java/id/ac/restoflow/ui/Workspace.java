package id.ac.restoflow.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.ac.restoflow.integration.Json;
import id.ac.restoflow.repository.RestaurantRepository;
import id.ac.restoflow.service.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.print.PrinterJob;
import javafx.geometry.Insets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.*;

/** Tampilan memakai snapshot server yang tunduk pada RLS; penulisan melalui RPC transaksional. */
public final class Workspace {
    @FunctionalInterface public interface Async {
        <T> void run(Callable<T> work,Consumer<T> success,Consumer<Throwable> failure);
    }
    private final Stage stage;
    private final RestaurantRepository repository;
    private final Async async;
    private final Runnable logout;
    private final BorderPane root=new BorderPane();
    private final DatePicker from=new DatePicker(Ui.today().minusDays(90)),to=new DatePicker(Ui.today());
    private final Map<String,Button> navButtons=new LinkedHashMap<>();
    private int zoomPercent=100;
    private JsonNode data;
    private String page;
    private PosView pos;

    public Workspace(Stage stage,RestaurantRepository repository,Async async,Runnable logout,JsonNode initial) {
        this.stage=stage;this.repository=repository;this.async=async;this.logout=logout;this.data=initial;
        root.addEventFilter(KeyEvent.KEY_PRESSED,this::zoomShortcut);
        page=switch(role()){case "kitchen"->"Dapur";case "courier"->"Pengiriman";case "waiter"->"POS";default->"Dashboard";};
        shell();show(page);
    }
    public BorderPane node(){return root;}
    public boolean hasUnsavedOrder(){return pos!=null&&!pos.cart.isEmpty();}
    private String role(){return data.path("profile").path("role").asText();}
    private boolean allowed(String... roles){return Arrays.asList(roles).contains(role());}
    private void shell(){
        navButtons.clear();
        VBox navigation=new VBox(6);navigation.getStyleClass().add("sidebar");navigation.setPrefWidth(235);
        Label brand=Ui.heading("RestoFlow");brand.getStyleClass().add("brand");
        Label caption=Ui.label("Rentang data · WIB");caption.getStyleClass().add("sidebar-caption");
        from.setAccessibleText("Tanggal mulai rentang data");to.setAccessibleText("Tanggal akhir rentang data");
        Label fromLabel=Ui.label("Dari");fromLabel.setLabelFor(from);
        Label toLabel=Ui.label("Sampai");toLabel.setLabelFor(to);
        Ui.dateFormat(from);Ui.dateFormat(to);
        from.setPrefWidth(200);to.setPrefWidth(200);
        from.setMaxWidth(Double.MAX_VALUE);to.setMaxWidth(Double.MAX_VALUE);
        Button applyRange=Ui.button("Terapkan rentang",this::refresh);
        applyRange.setWrapText(true);
        applyRange.getStyleClass().add("sidebar-action");applyRange.setMaxWidth(Double.MAX_VALUE);
        navigation.getChildren().addAll(brand,caption,fromLabel,from,toLabel,to,applyRange,new Separator());
        nav(navigation,"Dashboard","admin","cashier");nav(navigation,"POS","admin","cashier","waiter");
        nav(navigation,"Pesanan","admin","cashier","waiter");nav(navigation,"Dapur","admin","cashier","kitchen");
        nav(navigation,"Menu","admin","cashier","waiter");nav(navigation,"Meja","admin","cashier","waiter");
        nav(navigation,"Pembayaran","admin","cashier");nav(navigation,"Nota","admin","cashier");
        nav(navigation,"Staf","admin","cashier");nav(navigation,"Pengiriman","admin","cashier","courier");
        nav(navigation,"Ulasan","admin","cashier","courier");nav(navigation,"Laporan","admin","cashier");
        nav(navigation,"Pengaturan","admin");nav(navigation,"Akun","admin","cashier","kitchen","waiter","courier");
        Region spacer=new Region();VBox.setVgrow(spacer,Priority.ALWAYS);
        Button exit=Ui.button("Keluar",()->{
            if(pos==null||pos.cart.isEmpty()||Ui.confirm("Keranjang belum disimpan. Tetap keluar?"))logout.run();
        });exit.getStyleClass().add("logout-button");
        Label zoomHint=Ui.label("Zoom: Ctrl + + / Ctrl + - · Ctrl + 0 reset");zoomHint.getStyleClass().add("sidebar-caption");
        Label roleLabel=Ui.label("Peran: "+role());roleLabel.getStyleClass().add("sidebar-caption");
        Label loaded=Ui.label(Ui.rows(data,"orders").size()+" / "+data.path("order_count").asInt()+" pesanan dimuat");loaded.getStyleClass().add("sidebar-caption");
        navigation.getChildren().addAll(spacer,new Separator(),loaded,roleLabel,zoomHint,exit);
        ScrollPane navScroll=Ui.scroll(navigation);navScroll.setPrefWidth(235);navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);root.setLeft(navScroll);
        root.setTop(null);root.setBottom(null);
        setZoom(zoomPercent);
    }
    private void setZoom(int percent){
        zoomPercent=Math.max(80,Math.min(140,percent));
        root.setStyle("-fx-font-size: "+(13.0*zoomPercent/100)+"px;");
    }
    private void zoomShortcut(KeyEvent event){
        if(!event.isControlDown())return;
        switch(event.getCode()){
            case PLUS,ADD,EQUALS -> setZoom(zoomPercent+10);
            case MINUS,SUBTRACT -> setZoom(zoomPercent-10);
            case DIGIT0,NUMPAD0 -> setZoom(100);
            default -> {return;}
        }
        event.consume();
    }
    private void nav(VBox target,String label,String... roles){if(allowed(roles)){Button b=Ui.button(label,()->guard(()->show(label)));b.getStyleClass().add("nav-button");b.setMaxWidth(Double.MAX_VALUE);navButtons.put(label,b);target.getChildren().add(b);}}
    private static void guard(Runnable action){try{action.run();}catch(RuntimeException ex){Ui.error(ex.getMessage());}}
    public void refresh(){
        LocalDate start=from.getValue(),end=to.getValue();
        async.run(()->repository.snapshot(start,end),fresh->{
            data=fresh;if(pos!=null&&pos.cart.isEmpty())pos=null;
            shell();show(page);
        },error->Ui.error(error.getMessage()));
    }
    public void show(String page){
        this.page=page;
        navButtons.forEach((name,button)->{
            if(name.equals(page)){if(!button.getStyleClass().contains("active"))button.getStyleClass().add("active");}
            else button.getStyleClass().remove("active");
        });
        Node content=switch(page){
            case "Dashboard"->dashboard();case "POS"->pos();case "Pesanan"->orders(false);case "Dapur"->orders(true);
            case "Menu"->menus();case "Meja"->tables();case "Pembayaran"->payments(false);case "Nota"->payments(true);
            case "Staf"->staff();case "Pengiriman"->deliveries();case "Ulasan"->reviews();case "Laporan"->reports();
            case "Pengaturan"->settings();case "Akun"->account();default->throw new IllegalArgumentException("Halaman tidak dikenal.");};
        root.setCenter(content);
    }
    private void mutation(String action,ObjectNode payload,Consumer<JsonNode> after){
        async.run(()->repository.execute(action,payload),result->{after.accept(result);refresh();},error->Ui.error(error.getMessage()));
    }
    private Node page(String title,String help,Ui.DataTable table,Node... actions){
        FlowPane bar=new FlowPane(8,8,actions);VBox p=Ui.page(Ui.heading(title),Ui.label(help),bar,table.node);VBox.setVgrow(table.node,Priority.ALWAYS);return p;
    }
    private Node dashboard(){
        JsonNode s=data.path("stats");FlowPane metrics=new FlowPane(12,12,
            metric("Pendapatan lunas",Ui.money(s.path("revenue").asLong())),metric("Periode sebelumnya",Ui.money(s.path("previous_revenue").asLong())),metric("Total pesanan",""+s.path("orders").asLong()),
            metric("Pesanan aktif",""+s.path("active").asLong()),metric("Belum lunas",""+s.path("pending").asLong()));
        metrics.getStyleClass().add("summary-strip");
        var rows=Ui.rows(data,"orders").stream().filter(n->!Set.of("Selesai","Dibatalkan").contains(n.path("status").asText())).toList();
        Ui.DataTable active=orderTable(rows);Map<String,Long> sellers=new LinkedHashMap<>();
        for(JsonNode order:Ui.rows(data,"orders"))if(!"Dibatalkan".equals(order.path("status").asText()))
            order.path("items").forEach(i->sellers.merge(i.path("name").asText(),i.path("quantity").asLong(),Long::sum));
        String top=sellers.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(5)
            .map(e->e.getKey()+" ("+e.getValue()+")").reduce((a,b)->a+" · "+b).orElse("Belum ada penjualan pada rentang ini.");
        Label explanation=Ui.label("Ringkasan seluruh rentang tanggal. Pendapatan berasal dari pesanan lunas yang tidak dibatalkan.");explanation.getStyleClass().add("muted");
        Label bestSellers=Ui.label("Terlaris dari rincian yang dimuat: "+top);bestSellers.getStyleClass().add("muted");
        VBox p=Ui.page(Ui.heading("Dashboard"),explanation,metrics,Ui.heading("Antrean aktif"),active.node,bestSellers);
        VBox.setVgrow(active.node,Priority.ALWAYS);return p;
    }
    private Node metric(String title,String value){Label caption=Ui.label(title);caption.getStyleClass().add("muted");Label amount=Ui.heading(value);amount.getStyleClass().add("metric-value");VBox v=new VBox(5,caption,amount);v.getStyleClass().add("metric");v.setMinWidth(150);return v;}
    private Node pos(){
        if(pos==null)pos=new PosView(data,p->guard(()->{
            if(p.cart.isEmpty())throw new IllegalArgumentException("Keranjang kosong.");
            if(!Ui.confirm("Simpan pesanan? Harga final dihitung server berdasarkan harga menu saat ini."))return;
            if(p.editingId==null){
                ObjectNode payload=OrderService.prepare(p.cart,p.requestId,p.type.getValue(),Ui.integerChoice(p.table),p.notes.getText(),p.address.getText(),Ui.integerChoice(p.kitchen),Ui.stringChoice(p.waiter));
                async.run(()->repository.execute("create_order",payload),
                    result->{p.reset();page="Pesanan";refresh();},error->Ui.error(error.getMessage()));
            }else{
                ObjectNode payload=Json.object().put("id",p.editingId).put("type",p.type.getValue()).put("notes",p.notes.getText())
                    .put("address",p.address.getText()).put("waiter",Ui.stringChoice(p.waiter));
                if(Ui.integerChoice(p.table)!=null)payload.put("table_id",Ui.integerChoice(p.table));
                if(Ui.integerChoice(p.kitchen)!=null)payload.put("kitchen_id",Ui.integerChoice(p.kitchen));
                var items=payload.putArray("items");p.cart.items().forEach(i->items.addObject().put("menu_id",i.menuId()).put("quantity",i.quantity()));
                mutation("update_order",payload,result->{p.reset();page="Pesanan";});
            }
        }));return pos.node();
    }
    private Ui.DataTable orderTable(List<JsonNode> rows){return new Ui.DataTable(rows,Ui.col("No.","id"),Ui.col("Antrean","queue"),new Ui.Column("Waktu WIB",n->Ui.time(n.path("time"))),
        Ui.col("Meja","table_name"),Ui.col("Jenis","type"),Ui.col("Status","status"),Ui.col("Pembayaran","payment_status"),new Ui.Column("Item",this::itemSummary));}
    private String itemSummary(JsonNode order){List<String> lines=new ArrayList<>();order.path("items").forEach(n->lines.add(n.path("name").asText()+" ×"+n.path("quantity").asInt()));return String.join(", ",lines);}
    private Node orders(boolean kitchen){
        List<JsonNode> rows=Ui.rows(data,"orders");
        if(kitchen)rows=rows.stream().filter(n->Set.of("Menunggu","Diproses","Siap").contains(n.path("status").asText())).sorted(Comparator.comparing(n->n.path("time").asText())).toList();
        Ui.DataTable t=orderTable(rows);List<Node> actions=new ArrayList<>();
        actions.add(Ui.button("Rincian",()->guard(()->detail(t.selected()))));
        for(String state:new String[]{"Diproses","Siap","Selesai","Dibatalkan"}) {
            if(allowed("kitchen")&&!Set.of("Diproses","Siap").contains(state))continue;
            if(allowed("waiter")&&!state.equals("Selesai"))continue;
            Button statusButton=Ui.button(state,()->guard(()->{
                JsonNode order=t.selected();if(Ui.confirm("Ubah pesanan #"+order.path("id").asText()+" menjadi "+state+"?"))
                    async.run(()->repository.execute("order_status",Json.object().put("id",order.path("id").asInt()).put("status",state)),result->refresh(),error->{refresh();Ui.error(error.getMessage()+" Daftar sedang dimuat ulang; periksa status terbaru sebelum mencoba lagi.");});
            }));
            statusButton.setId("order-status-"+state);
            Runnable update=()->{String reason=OrderService.statusBlockReason(t.table.getSelectionModel().getSelectedItem(),role(),state);statusButton.setDisable(!reason.isEmpty());statusButton.setTooltip(new Tooltip(reason.isEmpty()?"Ubah status menjadi "+state:reason));};
            t.table.getSelectionModel().selectedItemProperty().addListener((obs,old,value)->update.run());update.run();actions.add(statusButton);
        }
        if(!kitchen&&allowed("admin","cashier","waiter")){
            Button edit=Ui.button("Edit isi pesanan",()->guard(()->{
            JsonNode order=t.selected();if(!"Menunggu".equals(order.path("status").asText())||!"Pending".equals(order.path("payment_status").asText()))throw new IllegalArgumentException("Isi pesanan hanya dapat diedit saat Menunggu dan belum dibayar. Gunakan Catat pembayaran untuk menerima pembayaran.");
            if(pos!=null&&!pos.cart.isEmpty()&&!Ui.confirm("Ganti keranjang saat ini dengan pesanan ini?"))return;
            pos=null;pos();pos.edit(order);show("POS");
            }));edit.setId("order-edit");
            Runnable updateEdit=()->edit.setDisable(t.table.getSelectionModel().getSelectedItem()==null
                ||!"Menunggu".equals(t.table.getSelectionModel().getSelectedItem().path("status").asText())
                ||!"Pending".equals(t.table.getSelectionModel().getSelectedItem().path("payment_status").asText()));
            t.table.getSelectionModel().selectedItemProperty().addListener((obs,old,value)->updateEdit.run());updateEdit.run();
            edit.setTooltip(new Tooltip("Mengubah item pesanan saat Menunggu. Untuk menerima uang, gunakan Catat pembayaran."));actions.add(edit);
        }
        if(!kitchen&&allowed("admin","cashier"))actions.add(paymentButton(t,"order-pay"));
        return page(kitchen?"Kitchen Display":"Pesanan",kitchen?"Pilih pesanan, lalu gunakan tombol status yang aktif. Menunggu → Diproses → Siap → Selesai. Selesai memerlukan pembayaran lunas. Terapkan rentang untuk memuat ulang.":"Gunakan Catat pembayaran untuk menerima pembayaran, termasuk saat pesanan Diproses atau Siap. Edit isi pesanan hanya saat Menunggu.",t,actions.toArray(Node[]::new));
    }
    private Button paymentButton(Ui.DataTable table,String id){
        Button button=Ui.button("Catat pembayaran",()->guard(()->pay(table.selected())));button.setId(id);
        Runnable update=()->{JsonNode selected=table.table.getSelectionModel().getSelectedItem();button.setDisable(selected==null||!"Pending".equals(selected.path("payment_status").asText())||"Dibatalkan".equals(selected.path("status").asText()));};
        table.table.getSelectionModel().selectedItemProperty().addListener((obs,old,value)->update.run());update.run();
        button.setTooltip(new Tooltip("Pilih pesanan dengan pembayaran Pending yang tidak dibatalkan. Pembayaran lunas tidak dicatat ulang."));return button;
    }
    private void detail(JsonNode order){
        Dialog<Void> d=new Dialog<>();Ui.styleDialog(d);d.initOwner(stage);d.setTitle("Pesanan #"+order.path("id").asText());
        TextArea text=new TextArea("Waktu: "+Ui.time(order.path("time"))+" WIB\nStatus: "+Ui.text(order,"status")+"\nMeja: "+Ui.text(order,"table_name")
            +"\nJenis: "+Ui.text(order,"type")+"\nCatatan: "+Ui.text(order,"notes")+"\nAlamat: "+Ui.text(order,"address")+"\n\n"+itemSummary(order));
        text.setEditable(false);text.setWrapText(true);text.setPrefSize(650,350);d.getDialogPane().setContent(text);d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);d.showAndWait();
    }
    private Node menus(){
        Ui.DataTable t=new Ui.DataTable(Ui.rows(data,"menu"),Ui.col("ID","id_menu"),Ui.col("Menu","nama_menu"),Ui.col("Kategori","category"),
            new Ui.Column("Harga",n->Ui.money(n.path("harga_satuan").asLong())),new Ui.Column("Aktif",n->n.path("is_active").asBoolean()?"Ya":"Diarsipkan"),Ui.col("Deskripsi","deskripsi_menu"));
        ComboBox<String> filter=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Semua kategori","Makanan","Minuman"));filter.setValue("Semua kategori");filter.setAccessibleText("Filter kategori menu");
        filter.setOnAction(e->t.setRowFilter(row->"Semua kategori".equals(filter.getValue())||filter.getValue().equals(row.path("category").asText())));
        return page("Menu","Kategori Makanan atau Minuman ditentukan melalui Edit / arsipkan. Kategori lama tetap tampil sampai diperbarui.",t,filter,
            adminButton("Tambah menu",()->editMenu(null)),adminButton("Edit / arsipkan",()->editMenu(t.selected())));
    }
    private Node adminButton(String title,Runnable action){Button b=Ui.button(title,()->guard(action));b.setVisible(allowed("admin"));b.setManaged(b.isVisible());return b;}
    private void editMenu(JsonNode row){
        TextField name=new TextField(value(row,"nama_menu")),price=new TextField(row==null?"0":row.path("harga_satuan").asText());
        ComboBox<String> category=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Makanan","Minuman"));category.setPromptText("Pilih kategori");
        if(row!=null){String previous=value(row,"category");if(!previous.isBlank()&&!category.getItems().contains(previous))category.getItems().add(previous);category.setValue(previous);}
        TextArea description=new TextArea(value(row,"deskripsi_menu"));description.setPrefRowCount(3);CheckBox active=active(row);
        GridPane f=Ui.form();Ui.field(f,0,"Nama menu",name);Ui.field(f,1,"Harga rupiah",price);Ui.field(f,2,"Kategori",category);Ui.field(f,3,"Deskripsi",description);Ui.field(f,4,"Aktif",active);
        editor("Menu",f,"save_menu",()->{
            if(category.getValue()==null||category.getValue().isBlank())throw new IllegalArgumentException("Pilih kategori Makanan atau Minuman.");
            ObjectNode p=Json.object().put("name",required(name)).put("price",Ui.amount(price)).put("category",category.getValue()).put("description",description.getText()).put("active",active.isSelected());
            if(row!=null)p.put("id",row.path("id_menu").asInt());return p;
        });
    }
    private Node tables(){
        Ui.DataTable t=new Ui.DataTable(Ui.rows(data,"tables"),Ui.col("ID","id_customer"),Ui.col("Meja","nomor_meja"),Ui.col("Kode QR","qr_meja"),new Ui.Column("Status",n->!n.path("is_active").asBoolean()?"Diarsipkan":busyTable(n.path("id_customer").asInt())?"Terisi":"Tersedia"));
        Node list=page("Meja","Status terisi dihitung dari seluruh pesanan aktif di server, terlepas dari rentang tanggal. Kode QR adalah identitas meja yang tersimpan.",t,
            adminButton("Tambah meja",()->editTable(null)),adminButton("Edit meja",()->editTable(t.selected())),Ui.button("Pesan di meja",()->guard(()->{
                JsonNode row=t.selected();if(!row.path("is_active").asBoolean())throw new IllegalArgumentException("Meja tidak aktif.");
                show("POS");pos.type.setValue("dine_in");Ui.select(pos.table,row.path("id_customer").asText());
            })));
        TilePane grid=new TilePane(14,14);grid.setPadding(new Insets(24));grid.setPrefTileWidth(170);
        for(JsonNode row:Ui.rows(data,"tables")){
            String status=!row.path("is_active").asBoolean()?"Diarsipkan":row.path("occupied").asBoolean()?"Terisi":"Tersedia";
            Button tile=Ui.button(Ui.text(row,"nomor_meja")+"\n"+status,()->guard(()->{
                show("POS");pos.type.setValue("dine_in");Ui.select(pos.table,row.path("id_customer").asText());
            }));tile.setPrefSize(170,100);tile.setDisable(!row.path("is_active").asBoolean());grid.getChildren().add(tile);
        }
        TabPane tabs=new TabPane();Tab visual=new Tab("Denah meja",Ui.scroll(grid)),details=new Tab("Daftar & pengelolaan",list);visual.setClosable(false);details.setClosable(false);tabs.getTabs().addAll(visual,details);return tabs;
    }
    private boolean busyTable(int id){return Ui.rows(data,"tables").stream().anyMatch(t->t.path("id_customer").asInt()==id&&t.path("occupied").asBoolean());}
    private void editTable(JsonNode row){
        TextField name=new TextField(value(row,"nomor_meja")),qr=new TextField(value(row,"qr_meja"));CheckBox active=active(row);
        GridPane f=Ui.form();Ui.field(f,0,"Nama meja",name);Ui.field(f,1,"Kode QR",qr);Ui.field(f,2,"Aktif",active);
        editor("Meja",f,"save_table",()->{ObjectNode p=Json.object().put("name",required(name)).put("qr",required(qr)).put("active",active.isSelected());if(row!=null)p.put("id",row.path("id_customer").asInt());return p;});
    }
    private Node payments(boolean receipts){
        Ui.DataTable t=new Ui.DataTable(Ui.rows(data,"orders"),Ui.col("Pesanan","id"),Ui.col("Antrean","queue"),Ui.col("Status pesanan","status"),Ui.col("Pembayaran","payment_status"),Ui.col("Metode","method"),
            new Ui.Column("Tagihan",n->Ui.money(n.path("total").asLong())));
        return page(receipts?"Nota & antrean":"Pembayaran",receipts?"Nota pending diberi penanda belum lunas. Harga lama dapat direkonstruksi dan selisih ditampilkan.":"Cash, Debit dan QRIS dicatat manual oleh kasir. Aplikasi tidak menghubungi payment gateway.",t,
            Ui.button("Lihat / cetak nota",()->guard(()->receipt(t.selected()))),
            receipts?new Label(""):paymentButton(t,"payment-pay"));
    }
    private void pay(JsonNode order){
        if(!"Pending".equals(order.path("payment_status").asText())||"Dibatalkan".equals(order.path("status").asText()))throw new IllegalArgumentException("Pembayaran tidak dapat diproses ulang.");
        ComboBox<String> method=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Cash","Debit","QRIS"));method.setValue("Cash");
        method.setId("payment-method");
        TextField received=new TextField(order.path("total").asText());Label change=Ui.label("");
        received.setId("payment-received");
        Runnable compute=()->{try{change.setText("Kembali: "+Ui.money(Ui.amount(received)-order.path("total").asLong()));}catch(RuntimeException ex){change.setText("Nominal belum valid.");}};
        received.textProperty().addListener((obs,a,b)->compute.run());compute.run();
        method.setOnAction(e->{received.setText(order.path("total").asText());received.setDisable(!"Cash".equals(method.getValue()));});
        GridPane f=Ui.form();Ui.field(f,0,"Tagihan",Ui.label(Ui.money(order.path("total").asLong())));Ui.field(f,1,"Metode",method);Ui.field(f,2,"Diterima",received);Ui.field(f,3,"Kembalian",change);
        VBox body=new VBox(14,Ui.label("Simpan hanya setelah uang diterima atau bukti pembayaran non-tunai diverifikasi. Konfirmasi ini bersifat manual."),f);
        editor("Konfirmasi pembayaran #"+order.path("id").asText(),body,"pay",()->{
            long amount=Ui.amount(received);if(amount<order.path("total").asLong())throw new IllegalArgumentException("Nominal diterima kurang.");
            return Json.object().put("id",order.path("id").asInt()).put("method",method.getValue()).put("received",amount);
        });
    }
    private void receipt(JsonNode order){
        String text=ReceiptService.render(order,data.path("settings"));TextArea preview=new TextArea(text);preview.setEditable(false);preview.setWrapText(true);preview.setStyle("-fx-font-family: 'Consolas';");preview.setPrefSize(650,500);
        Dialog<Void> d=new Dialog<>();Ui.styleDialog(d);d.initOwner(stage);d.setTitle("Nota #"+order.path("id").asText());
        d.getDialogPane().setContent(new VBox(12,preview,Ui.button("Cetak",()->print(text)),Ui.button("Simpan TXT",()->saveText("nota-"+order.path("id").asText()+".txt",text))));
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);d.showAndWait();
    }
    private void print(String text){
        PrinterJob job=PrinterJob.createPrinterJob();if(job==null){Ui.error("Printer tidak tersedia. Simpan nota sebagai TXT atau pasang printer PDF.");return;}
        if(!job.showPrintDialog(stage))return;
        double height=job.getJobSettings().getPageLayout().getPrintableHeight();int perPage=Math.max(1,(int)(height/16)-2);
        int lineWidth=Math.max(10,Math.min(60,(int)((job.getJobSettings().getPageLayout().getPrintableWidth()-16)/6.4)));
        List<String> wrapped=new ArrayList<>();for(String line:text.split("\n")){
            if(line.isEmpty())wrapped.add("");else for(int i=0;i<line.length();i+=lineWidth)wrapped.add(line.substring(i,Math.min(i+lineWidth,line.length())));
        }
        for(int start=0;start<wrapped.size();start+=perPage){
            VBox sheet=new VBox(2);sheet.setPadding(new Insets(8));sheet.setPrefWidth(job.getJobSettings().getPageLayout().getPrintableWidth());
            for(String line:wrapped.subList(start,Math.min(start+perPage,wrapped.size()))){Label l=Ui.label(line);l.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 10px;");sheet.getChildren().add(l);}
            if(!job.printPage(sheet)){job.cancelJob();Ui.error("Pencetakan gagal.");return;}
        }
        if(!job.endJob())Ui.error("Printer tidak mengonfirmasi penyelesaian pekerjaan.");
    }
    private Node staff(){
        Ui.DataTable staff=new Ui.DataTable(Ui.rows(data,"staff"),Ui.col("ID","id_staff"),new Ui.Column("Nama / peran",n->Ui.staff(n).displayLabel()),new Ui.Column("Penugasan aktif dimuat",n->""+workload(n)),new Ui.Column("Aktif",n->""+n.path("is_active").asBoolean()));
        Ui.DataTable waiters=new Ui.DataTable(Ui.rows(data,"waiters"),Ui.col("ID pelayan","id_pelayan"),Ui.col("Nama depan","nama_depan"),Ui.col("Nama belakang","nama_belakang"),new Ui.Column("Aktif",n->""+n.path("is_active").asBoolean()));
        TabPane tabs=new TabPane();Tab a=new Tab("Dapur & kurir",new VBox(12,new FlowPane(10,10,adminButton("Tambah staf",()->editStaff(null,false)),adminButton("Edit staf",()->editStaff(staff.selected(),false))),staff.node));
        Tab b=new Tab("Pelayan",new VBox(12,new FlowPane(10,10,adminButton("Tambah pelayan",()->editStaff(null,true)),adminButton("Edit pelayan",()->editStaff(waiters.selected(),true))),waiters.node));a.setClosable(false);b.setClosable(false);tabs.getTabs().addAll(a,b);
        VBox body=Ui.page(Ui.heading("Staf"),Ui.label("Pelayan tetap memiliki identitas terpisah. Arsip mempertahankan data riwayat. Penugasan aktif dapat dilihat pada rincian pesanan/pengiriman."),tabs);VBox.setVgrow(tabs,Priority.ALWAYS);return body;
    }
    private void editStaff(JsonNode row,boolean waiter){
        TextField first=new TextField(value(row,"nama_depan")),last=new TextField(value(row,"nama_belakang")),id=new TextField(value(row,"id_pelayan"));id.setDisable(row!=null);
        ComboBox<String> kind=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("kitchen","courier"));kind.setValue(row==null?"kitchen":value(row,"kind"));kind.setDisable(row!=null);
        CheckBox active=active(row);GridPane f=Ui.form();Ui.field(f,0,"Nama depan",first);Ui.field(f,1,"Nama belakang",last);Ui.field(f,2,waiter?"ID pelayan":"Jenis staf",waiter?id:kind);Ui.field(f,3,"Aktif",active);
        editor(waiter?"Pelayan":"Staf",f,waiter?"save_waiter":"save_staff",()->{
            ObjectNode p=Json.object().put("first",required(first)).put("last",required(last)).put("active",active.isSelected());
            if(waiter)p.put("waiter_id",required(id)).put("is_new",row==null);else{p.put("kind",kind.getValue());if(row!=null)p.put("id",row.path("id_staff").asInt());}return p;
        });
    }
    private long workload(JsonNode staff){
        String id=staff.path("id_staff").asText();
        if("kitchen".equals(staff.path("kind").asText()))return Ui.rows(data,"orders").stream().filter(o->!Set.of("Selesai","Dibatalkan").contains(o.path("status").asText()))
            .filter(o->{for(JsonNode a:o.path("assignments"))if(id.equals(a.path("id_staff").asText()))return true;return false;}).count();
        String courier=Ui.rows(data,"couriers").stream().filter(c->id.equals(c.path("id_staff").asText())).map(c->c.path("id_kurir").asText()).findFirst().orElse("");
        return Ui.rows(data,"orders").stream().filter(o->courier.equals(o.path("courier_id").asText())&&Set.of("Ditugaskan","Diambil").contains(o.path("delivery_status").asText())).count();
    }
    private Node deliveries(){
        Ui.DataTable t=new Ui.DataTable(Ui.rows(data,"orders").stream().filter(n->!n.path("delivery_status").isNull()&&n.has("delivery_status")).toList(),
            Ui.col("Pesanan","id"),Ui.col("Status","delivery_status"),Ui.col("Alamat","address"),
            new Ui.Column("Kurir",n->courierName(n.path("courier_id").asText())),Ui.col("Pesanan","status"),new Ui.Column("Diambil WIB",n->Ui.time(n.path("waktu_diambil"))),new Ui.Column("Sampai WIB",n->Ui.time(n.path("waktu_sampai"))));
        List<Node> actions=new ArrayList<>();if(allowed("admin","cashier"))actions.add(Ui.button("Tugaskan kurir",()->guard(()->{
            JsonNode order=t.selected();ComboBox<Ui.Choice> courier=courierChoices();
            editor("Tugaskan kurir",new VBox(12,Ui.label("Pilih kurir aktif. Riwayat tidak dipetakan ke kurir secara otomatis."),courier),"assign_delivery",()->{
                Integer id=Ui.integerChoice(courier);if(id==null)throw new IllegalArgumentException("Pilih kurir.");return Json.object().put("id",order.path("id").asInt()).put("courier_id",id);
            });
        })));
        for(String state:new String[]{"Diambil","Terkirim"})actions.add(Ui.button(state,()->guard(()->{
            JsonNode order=t.selected();if(Ui.confirm("Konfirmasi pengiriman #"+order.path("id").asText()+" "+state+"?"))mutation("delivery_status",Json.object().put("id",order.path("id").asInt()).put("status",state),r->{});
        })));
        return page("Pengiriman","Alur: Menunggu → Ditugaskan → Diambil → Terkirim. Pengambilan memerlukan pesanan siap dan lunas. Tidak ada pelacakan GPS.",t,actions.toArray(Node[]::new));
    }
    private String courierName(String id){String staff=Ui.rows(data,"couriers").stream().filter(n->n.path("id_kurir").asText().equals(id)).map(n->n.path("id_staff").asText()).findFirst().orElse("");return staff.isBlank()?"Belum ditugaskan":Ui.name(data,"staff","id_staff",staff,"nama_staff");}
    private ComboBox<Ui.Choice> courierChoices(){return Ui.choices(data,"couriers","id_kurir",n->{
        long load=Ui.rows(data,"orders").stream().filter(o->o.path("courier_id").asInt()==n.path("id_kurir").asInt()&&Set.of("Ditugaskan","Diambil").contains(o.path("delivery_status").asText())).count();
        return courierName(n.path("id_kurir").asText())+" · aktif dimuat: "+load;
    },false);}
    private Node reviews(){
        var rows=Ui.rows(data,"reviews");double avg=rows.stream().mapToInt(n->n.path("rating").asInt()).average().orElse(0);
        String distribution="";for(int star=1;star<=5;star++){final int n=star;distribution+=star+"★: "+rows.stream().filter(r->r.path("rating").asInt()==n).count()+"   ";}
        Ui.DataTable t=new Ui.DataTable(rows,Ui.col("Tanggal","tanggal_rating"),new Ui.Column("Kurir",n->courierName(n.path("id_kurir").asText())),Ui.col("Rating","rating"),Ui.col("Komentar","ulasan"));
        Button add=Ui.button("Catat ulasan",()->guard(()->{
            ComboBox<Ui.Choice> courier=courierChoices();Spinner<Integer> rating=new Spinner<>(1,5,5);DatePicker date=new DatePicker(LocalDate.now());TextArea comment=new TextArea();comment.setPrefRowCount(3);
            GridPane f=Ui.form();Ui.field(f,0,"Kurir",courier);Ui.field(f,1,"Tanggal",date);Ui.field(f,2,"Rating",rating);Ui.field(f,3,"Komentar",comment);
            editor("Ulasan kurir",f,"review",()->{if(courier.getValue()==null||date.getValue()==null)throw new IllegalArgumentException("Pilih kurir dan tanggal.");return Json.object().put("courier_id",Ui.integerChoice(courier)).put("date",date.getValue().toString()).put("rating",rating.getValue()).put("comment",comment.getText());});
        }));add.setVisible(allowed("admin","cashier"));add.setManaged(add.isVisible());
        return page("Ulasan",String.format(Locale.forLanguageTag("id-ID"),"Rata-rata %.2f dari %d ulasan. %s Ulasan terkait kurir, bukan pesanan tertentu. Cari ID/rating/tanggal melalui pencarian.",avg,rows.size(),distribution),t,add);
    }
    private Node reports(){
        Ui.DataTable t=new Ui.DataTable(Ui.rows(data,"orders"),Ui.col("Pesanan","id"),new Ui.Column("Waktu WIB",n->Ui.time(n.path("time"))),Ui.col("Status","status"),Ui.col("Pembayaran","payment_status"),Ui.col("Metode","method"),
            new Ui.Column("Total",n->Ui.money(n.path("total").asLong())),new Ui.Column("Selisih rincian",n->Ui.money(n.path("total").asLong()-lineTotal(n))));
        Map<String,Long> paid=new TreeMap<>();Map<String,Long> days=new TreeMap<>();
        for(JsonNode o:Ui.rows(data,"orders"))if("Lunas".equals(o.path("payment_status").asText())&&!"Dibatalkan".equals(o.path("status").asText())){
            paid.merge(o.path("method").asText(),o.path("total").asLong(),Long::sum);
            days.merge(java.time.Instant.parse(o.path("time").asText()).atZone(java.time.ZoneId.of("Asia/Jakarta")).toLocalDate().toString(),o.path("total").asLong(),Long::sum);
        }
        String methods=paid.entrySet().stream().map(e->e.getKey()+": "+Ui.money(e.getValue())).reduce((a,b)->a+" · "+b).orElse("Belum ada pembayaran lunas.");
        return page("Laporan & rekonsiliasi","Ringkasan rincian yang dimuat: "+methods+". Ekspor mengikuti pencarian dan rentang tanggal. Harga lama yang tidak memiliki snapshot memakai harga menu terkini untuk pembanding; total historis tidak diubah.",t,
            Ui.button("Ekspor transaksi CSV",()->{
                List<List<String>> csv=new ArrayList<>();csv.add(List.of("Pesanan","Waktu WIB","Jenis","Status","Pembayaran","Metode","Total historis","Total rincian pembanding","Selisih"));
                String q=t.search.getText().toLowerCase(Locale.ROOT).trim();
                for(JsonNode o:Ui.rows(data,"orders"))if(Json.write(o).toLowerCase(Locale.ROOT).contains(q))csv.add(List.of(o.path("id").asText(),Ui.time(o.path("time")),Ui.text(o,"type"),Ui.text(o,"status"),Ui.text(o,"payment_status"),Ui.text(o,"method"),o.path("total").asText(),""+lineTotal(o),""+(o.path("total").asLong()-lineTotal(o))));
                saveText("laporan-"+from.getValue()+"-"+to.getValue()+".csv",CsvExport.render(csv));
            }),Ui.button("Tren penjualan",()->{
                Dialog<Void> d=new Dialog<>();Ui.styleDialog(d);d.initOwner(stage);d.setTitle("Pendapatan lunas pada rincian yang dimuat");
                var x=new javafx.scene.chart.CategoryAxis();var y=new javafx.scene.chart.NumberAxis();var chart=new javafx.scene.chart.BarChart<String,Number>(x,y);chart.setAnimated(false);chart.setLegendVisible(false);
                ComboBox<String> mode=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Harian","Mingguan","Bulanan"));mode.setValue("Harian");
                Runnable draw=()->{
                    Map<String,Long> grouped=new TreeMap<>();days.forEach((day,total)->{
                        LocalDate key=LocalDate.parse(day);if("Mingguan".equals(mode.getValue()))key=key.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                        if("Bulanan".equals(mode.getValue()))key=key.withDayOfMonth(1);grouped.merge(key.toString(),total,Long::sum);
                    });
                    var series=new javafx.scene.chart.XYChart.Series<String,Number>();grouped.forEach((day,total)->series.getData().add(new javafx.scene.chart.XYChart.Data<>(day,total)));chart.getData().setAll(List.of(series));
                };mode.setOnAction(e->draw.run());draw.run();chart.setPrefSize(800,450);
                d.getDialogPane().setContent(new VBox(12,mode,chart));d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);d.showAndWait();
            }));
    }
    private long lineTotal(JsonNode o){long total=0;for(JsonNode i:o.path("items"))total=Math.addExact(total,Math.multiplyExact(i.path("quantity").asLong(),i.path("price").asLong()));return total;}
    private Node settings(){
        TextField name=new TextField(data.path("settings").path("display_name").asText());TextArea footer=new TextArea(data.path("settings").path("receipt_footer").asText());footer.setPrefRowCount(3);
        GridPane fields=Ui.form();Ui.field(fields,0,"Nama restoran",name);Ui.field(fields,1,"Footer nota",footer);
        Ui.DataTable profiles=new Ui.DataTable(Ui.rows(data,"profiles"),Ui.col("Nama","display_name"),Ui.col("Peran","role"),Ui.col("User UUID","id"),Ui.col("Staf","id_staff"),Ui.col("Pelayan","id_pelayan"),new Ui.Column("Aktif",n->""+n.path("is_active").asBoolean()));
        VBox p=Ui.page(Ui.heading("Pengaturan"),Ui.label("Mata uang IDR · waktu Asia/Jakarta. Pengaturan ini disimpan bersama di database."),fields,
            Ui.button("Simpan pengaturan",()->guard(()->mutation("settings",Json.object().put("name",required(name)).put("footer",footer.getText()),r->{}))),
            Ui.heading("Hak akses pengguna"),Ui.label("Buat akun email/password melalui Supabase Auth terlebih dahulu, lalu hubungkan UUID-nya di sini. Aplikasi tidak membawa kunci administrator Auth."),
            new FlowPane(10,10,Ui.button("Tambah profil",()->editProfile(null)),Ui.button("Edit profil",()->guard(()->editProfile(profiles.selected())))),profiles.node);
        return Ui.scroll(p);
    }
    private void editProfile(JsonNode row){
        TextField uuid=new TextField(value(row,"id")),name=new TextField(value(row,"display_name"));uuid.setDisable(row!=null);
        ComboBox<String> role=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("admin","cashier","kitchen","waiter","courier"));role.setValue(row==null?"cashier":value(row,"role"));
        ComboBox<Ui.Choice> staff=Ui.choices(data,"staff","id_staff",n->Ui.text(n,"nama_staff"),true),waiter=Ui.choices(data,"waiters","id_pelayan",n->Ui.text(n,"id_pelayan")+" · "+Ui.text(n,"nama_depan"),true);
        if(row!=null){Ui.select(staff,row.path("id_staff").asText());Ui.select(waiter,row.path("id_pelayan").asText());}
        CheckBox active=active(row);GridPane f=Ui.form();Ui.field(f,0,"UUID Auth user",uuid);Ui.field(f,1,"Nama tampilan",name);Ui.field(f,2,"Peran",role);Ui.field(f,3,"Staf dapur/kurir",staff);Ui.field(f,4,"Identitas pelayan",waiter);Ui.field(f,5,"Aktif",active);
        editor("Profil pengguna",f,"save_profile",()->{
            UUID.fromString(required(uuid));ObjectNode p=Json.object().put("user_id",uuid.getText().trim()).put("name",required(name)).put("role",role.getValue()).put("active",active.isSelected());
            if(Ui.integerChoice(staff)!=null)p.put("staff_id",Ui.integerChoice(staff));p.put("waiter_id",Ui.stringChoice(waiter));return p;
        });
    }
    private Node account(){return Ui.page(Ui.heading("Akun saya"),Ui.label("Nama: "+data.path("profile").path("display_name").asText()),Ui.label("Peran: "+role()),Ui.label("UUID: "+data.path("profile").path("id").asText()),
        Ui.label("Versi 1.0.3 · JavaFX Desktop · IDR · Asia/Jakarta"),Ui.label("Hak akses diperiksa oleh server. Perubahan peran dilakukan admin lain. Sesi dihapus saat keluar dan tidak ditulis ke disk."));}
    private static String value(JsonNode n,String key){return n==null||n.path(key).isNull()?"":n.path(key).asText("");}
    private static CheckBox active(JsonNode row){CheckBox box=new CheckBox("Aktif");box.setSelected(row==null||row.path("is_active").asBoolean());return box;}
    private static String required(TextField field){String value=field.getText().trim();if(value.isBlank())throw new IllegalArgumentException("Kolom wajib belum diisi.");return value;}
    private void editor(String title,Node fields,String action,Supplier<ObjectNode> payload){
        Dialog<Void> dialog=new Dialog<>();Ui.styleDialog(dialog);dialog.initOwner(stage);dialog.setTitle(title);dialog.getDialogPane().setPrefWidth(650);
        dialog.getDialogPane().setContent(Ui.scroll(Ui.page(fields)));dialog.getDialogPane().getButtonTypes().addAll(Ui.SAVE,ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(Ui.SAVE).addEventFilter(javafx.event.ActionEvent.ACTION,event->{
            event.consume();guard(()->{
                ObjectNode body=payload.get();if(action.equals("pay")&&!Ui.confirm("Konfirmasi bahwa pembayaran sudah benar-benar diterima?"))return;
                dialog.getDialogPane().setDisable(true);
                async.run(()->repository.execute(action,body),result->{dialog.close();refresh();},error->{dialog.getDialogPane().setDisable(false);Ui.error(error.getMessage());});
            });
        });dialog.showAndWait();
    }
    private void saveText(String suggested,String text){
        FileChooser chooser=new FileChooser();chooser.setTitle("Simpan hasil");chooser.setInitialFileName(suggested);var file=chooser.showSaveDialog(stage);if(file==null)return;
        try{Files.writeString(file.toPath(),text,StandardCharsets.UTF_8);}catch(Exception ex){Ui.error("Berkas tidak dapat disimpan. Periksa izin folder.");}
    }
}

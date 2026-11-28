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
    private final CheckBox autoRefreshToggle=new CheckBox("Auto-refresh (15d)");
    private final javafx.animation.Timeline autoRefreshTimer;
    private final Label toastLabel=Ui.label("");
    private final javafx.animation.PauseTransition toastHideTimer=new javafx.animation.PauseTransition(javafx.util.Duration.seconds(4));
    private String lastUpdatedTime=currentWibTime();
    private int zoomPercent=100;
    private JsonNode data;
    private String page;
    private PosView pos;

    public Workspace(Stage stage,RestaurantRepository repository,Async async,Runnable logout,JsonNode initial) {
        this.stage=stage;this.repository=repository;this.async=async;this.logout=logout;this.data=initial;
        toastLabel.getStyleClass().add("toast-banner");
        toastLabel.setId("workspace-toast");
        toastLabel.setVisible(false);
        toastLabel.setManaged(false);
        toastLabel.setMaxWidth(Double.MAX_VALUE);
        toastHideTimer.setOnFinished(e->{toastLabel.setVisible(false);toastLabel.setManaged(false);});
        autoRefreshToggle.setId("sidebar-autorefresh");
        autoRefreshToggle.getStyleClass().add("sidebar-caption");
        autoRefreshToggle.setTooltip(new Tooltip("Muat ulang antrean & status meja otomatis setiap 15 detik saat tidak sedang mengedit pesanan."));
        autoRefreshTimer=new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(15),e->autoRefreshTick()));
        autoRefreshTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        autoRefreshToggle.selectedProperty().addListener((obs,oldVal,enabled)->{
            if(enabled)autoRefreshTimer.playFromStart();
            else autoRefreshTimer.stop();
        });
        root.addEventFilter(KeyEvent.KEY_PRESSED,this::zoomShortcut);
        page=switch(role()){case "kitchen"->"Dapur";case "courier"->"Pengiriman";case "waiter"->"POS";default->"Dashboard";};
        shell();show(page);
    }
    private static String currentWibTime(){
        return java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Jakarta"))
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    public void showToast(String message){
        if(message==null||message.isBlank())return;
        toastLabel.setText(message);
        toastLabel.setVisible(true);
        toastLabel.setManaged(true);
        toastHideTimer.playFromStart();
    }
    private void autoRefreshTick(){
        if(!autoRefreshToggle.isSelected()||!stage.isShowing()||hasUnsavedOrder())return;
        boolean secondaryDialogOpen=javafx.stage.Window.getWindows().stream().anyMatch(w->w!=stage&&w.isShowing());
        if(secondaryDialogOpen)return;
        Node focusOwner=stage.getScene()==null?null:stage.getScene().getFocusOwner();
        if(focusOwner instanceof TextInputControl)return;
        refresh(true);
    }
    public BorderPane node(){return root;}
    public boolean hasUnsavedOrder(){return pos!=null&&!pos.cart.isEmpty();}
    private String role(){return data.path("profile").path("role").asText();}
    private boolean allowed(String... roles){return Arrays.asList(roles).contains(role());}
    private static final String ICON_BRAND = "M3 14h18v2H3v-2zm2-1c0-3.87 3.13-7 7-7s7 3.13 7 7H5zm6-9h2v2h-2V4z";
    private static final String ICON_CALENDAR = "M19 4h-1V2h-2v2H8V2H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zm0-12H5V6h14v2z";
    private static final String ICON_REFRESH = "M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";
    private static final String ICON_LOGOUT = "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5-5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z";

    private static String navIconSvg(String label) {
        return switch (label) {
            case "Dashboard" -> "M3 3h8v8H3V3zm10 0h8v8h-8V3zM3 13h8v8H3v-8zm10 0h8v8h-8v-8z";
            case "POS" -> "M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zm10 0c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2zM7.17 14.75l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49A1 1 0 0 0 20 4H5.21l-.94-2H1v2h2l3.6 7.59-1.35 2.45C4.52 15.37 5.48 17 7 17h12v-2H7.42c-.14 0-.25-.11-.25-.25z";
            case "Pesanan" -> "M19 3h-4.18C14.4 1.84 13.3 1 12 1s-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm5 14H7v-2h10v2zm0-4H7v-2h10v2zm0-4H7V7h10v2z";
            case "Dapur" -> "M13.5.67s.74 2.65.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73l.03-.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 13.5.67zM11.71 19c-1.78 0-3.22-1.4-3.22-3.14 0-1.62 1.05-2.76 2.81-3.12 1.77-.36 3.6-1.21 4.62-2.58.39 1.29.59 2.65.59 4.04 0 2.65-2.15 4.8-4.8 4.8z";
            case "Menu" -> "M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z";
            case "Meja" -> "M4 6h16v3H4V6zm2 4h2v9H6v-9zm10 0h2v9h-2v-9zm-7 1h6v2H9v-2z";
            case "Staf" -> "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z";
            case "Pengiriman" -> "M20 8h-3V4H3c-1.1 0-2 .9-2 2v11h2c0 1.66 1.34 3 3 3s3-1.34 3-3h6c0 1.66 1.34 3 3 3s3-1.34 3-3h2v-5l-3-4zM6 18.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm13.5-9l1.96 2.5H17V9.5h2.5zm-1.5 9c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z";
            case "Ulasan" -> "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z";
            case "Laporan" -> "M5 9.2h3V19H5V9.2zM10.6 5h2.8v14h-2.8V5zm5.6 8H19v6h-2.8v-6z";
            case "Pengaturan" -> "M19.14 12.94c.04-.31.06-.63.06-.94s-.02-.63-.06-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96a7.03 7.03 0 0 0-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.49.49 0 0 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.04.31-.06.63-.06.94s.02.63.06.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
            default -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z";
        };
    }

    private void navGroupLabel(VBox target, String title, String... roles) {
        if (allowed(roles)) {
            Label l = Ui.label(title);
            l.getStyleClass().add("nav-group-label");
            target.getChildren().add(l);
        }
    }

    private void shell(){
        navButtons.clear();
        VBox navigation=new VBox(8);navigation.getStyleClass().add("sidebar");navigation.setPrefWidth(248);

        // Zona 1: Header Brand & Pill Badge Peran
        StackPane brandIcon=Ui.icon(ICON_BRAND,16);brandIcon.getStyleClass().add("brand-icon-box");
        Label brand=Ui.heading("RestoFlow");brand.getStyleClass().add("brand");
        HBox brandHeader=new HBox(10,brandIcon,brand);
        brandHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        brandHeader.getStyleClass().add("brand-header");

        Region dot=new Region();dot.getStyleClass().add("role-badge-dot");
        dot.setMinSize(7,7);dot.setPrefSize(7,7);dot.setMaxSize(7,7);
        Label roleText=Ui.label("Peran: "+role());roleText.getStyleClass().add("role-badge-text");
        HBox roleBadge=new HBox(7,dot,roleText);
        roleBadge.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        roleBadge.getStyleClass().add("role-badge");
        roleBadge.setMaxWidth(Region.USE_PREF_SIZE);

        // Zona 2: Kartu Filter Rentang Data (Compact Filter Card)
        Label caption=Ui.label("Rentang Data (WIB)");caption.getStyleClass().addAll("sidebar-caption","sidebar-filter-title");
        HBox filterHeader=new HBox(6,Ui.icon(ICON_CALENDAR,13),caption);
        filterHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        from.setAccessibleText("Tanggal mulai rentang data");to.setAccessibleText("Tanggal akhir rentang data");
        Label fromLabel=Ui.label("Dari");fromLabel.setLabelFor(from);fromLabel.getStyleClass().add("sidebar-field-label");
        Label toLabel=Ui.label("Sampai");toLabel.setLabelFor(to);toLabel.getStyleClass().add("sidebar-field-label");
        Ui.dateFormat(from);Ui.dateFormat(to);
        from.setPrefWidth(200);to.setPrefWidth(200);
        from.setMaxWidth(Double.MAX_VALUE);to.setMaxWidth(Double.MAX_VALUE);
        Button presetToday=presetButton("Hari ini",0);
        Button preset7d=presetButton("7 Hari",7);
        Button preset30d=presetButton("30 Hari",30);
        HBox presets=new HBox(4,presetToday,preset7d,preset30d);
        Button applyRange=Ui.button("Terapkan rentang",this::refresh);
        applyRange.setGraphic(Ui.icon(ICON_REFRESH,14));
        applyRange.setContentDisplay(ContentDisplay.LEFT);
        applyRange.setGraphicTextGap(8);
        applyRange.setWrapText(true);
        applyRange.getStyleClass().add("sidebar-action");applyRange.setMaxWidth(Double.MAX_VALUE);
        VBox filterCard=new VBox(6,filterHeader,fromLabel,from,toLabel,to,presets,applyRange,autoRefreshToggle);
        filterCard.getStyleClass().add("sidebar-filter-card");

        navigation.getChildren().addAll(brandHeader,roleBadge,filterCard,toastLabel);

        // Zona 3: Daftar Navigasi Berikon & Dikelompokkan
        VBox navList=new VBox(3);navList.getStyleClass().add("nav-list");
        navGroupLabel(navList,"UTAMA & TRANSAKSI","admin","cashier","waiter","kitchen");
        nav(navList,"Dashboard","admin","cashier");
        nav(navList,"POS","admin","cashier","waiter");
        nav(navList,"Pesanan","admin","cashier","waiter");
        nav(navList,"Dapur","kitchen");

        navGroupLabel(navList,"OPERASIONAL & KATALOG","admin","cashier","waiter","courier");
        nav(navList,"Menu","admin","cashier","waiter");
        nav(navList,"Meja","admin","cashier","waiter");
        nav(navList,"Pengiriman","admin","cashier","courier");

        navGroupLabel(navList,"MANAJEMEN & SISTEM","admin","cashier","kitchen","waiter","courier");
        nav(navList,"Staf","admin","cashier");
        nav(navList,"Ulasan","admin","cashier","courier");
        nav(navList,"Laporan","admin","cashier");
        nav(navList,"Akun","admin","cashier","kitchen","waiter","courier");
        navigation.getChildren().add(navList);

        // Zona 4: Footer Status & Tombol Keluar
        Region spacer=new Region();VBox.setVgrow(spacer,Priority.ALWAYS);
        int loadedCount=Ui.rows(data,"orders").size();
        int totalCount=data.path("order_count").asInt();
        double ratio=totalCount>0?Math.min(1.0,(double)loadedCount/totalCount):1.0;
        Label loaded=Ui.label(loadedCount+" / "+totalCount+" pesanan dimuat");loaded.getStyleClass().add("sidebar-status-title");
        ProgressBar progress=new ProgressBar(ratio);progress.getStyleClass().add("sidebar-progress");progress.setMaxWidth(Double.MAX_VALUE);
        Label updatedLabel=Ui.label("Diperbarui: "+lastUpdatedTime+" WIB");updatedLabel.getStyleClass().add("sidebar-caption");
        VBox statusBox=new VBox(5,loaded,progress,updatedLabel);statusBox.getStyleClass().add("sidebar-status-box");

        Button exit=Ui.button("Keluar",()->{
            if(pos==null||pos.cart.isEmpty()||Ui.confirm("Keranjang belum disimpan. Tetap keluar?")){
                autoRefreshTimer.stop();
                logout.run();
            }
        });
        exit.setGraphic(Ui.icon(ICON_LOGOUT,15));
        exit.setContentDisplay(ContentDisplay.LEFT);
        exit.setGraphicTextGap(10);
        exit.getStyleClass().add("logout-button");
        exit.setMaxWidth(Double.MAX_VALUE);

        navigation.getChildren().addAll(spacer,statusBox,exit);
        ScrollPane navScroll=Ui.scroll(navigation);navScroll.setPrefWidth(248);navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);root.setLeft(navScroll);
        root.setTop(null);root.setBottom(null);
        setZoom(zoomPercent);
    }
    private Button presetButton(String title,int daysBack){
        Button b=Ui.button(title,()->{
            LocalDate today=Ui.today();
            from.setValue(today.minusDays(daysBack));
            to.setValue(today);
            refresh();
        });
        b.getStyleClass().add("preset-button");
        HBox.setHgrow(b,Priority.ALWAYS);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }
    private void setZoom(int percent){
        zoomPercent=Math.max(80,Math.min(140,percent));
        root.setStyle("-fx-font-size: "+(13.0*zoomPercent/100)+"px;");
    }
    private void zoomShortcut(KeyEvent event){
        if(event.getCode()==KeyCode.F5||(event.isControlDown()&&event.getCode()==KeyCode.R)){
            refresh();
            event.consume();
            return;
        }
        if(!event.isControlDown())return;
        switch(event.getCode()){
            case PLUS,ADD,EQUALS -> setZoom(zoomPercent+10);
            case MINUS,SUBTRACT -> setZoom(zoomPercent-10);
            case DIGIT0,NUMPAD0 -> setZoom(100);
            default -> {return;}
        }
        event.consume();
    }
    private void nav(VBox target,String label,String... roles){
        if(allowed(roles)){
            Button b=Ui.button(label,()->guard(()->show(label)));
            b.setGraphic(Ui.icon(navIconSvg(label),16));
            b.setContentDisplay(ContentDisplay.LEFT);
            b.setGraphicTextGap(12);
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            navButtons.put(label,b);
            target.getChildren().add(b);
        }
    }
    private static void guard(Runnable action){try{action.run();}catch(RuntimeException ex){Ui.error(ex.getMessage());}}
    public void refresh(){
        refresh(false);
    }
    private void refresh(boolean backgroundPoll){
        LocalDate start=from.getValue(),end=to.getValue();
        long prevActive=countByStates(data,Set.of("Menunggu","Diproses","Siap"));
        long prevReady=countByStates(data,Set.of("Siap"));
        async.run(()->repository.snapshot(start,end),fresh->{
            long newActive=countByStates(fresh,Set.of("Menunggu","Diproses","Siap"));
            long newReady=countByStates(fresh,Set.of("Siap"));
            data=fresh;
            lastUpdatedTime=currentWibTime();
            if(pos!=null&&pos.cart.isEmpty())pos=null;
            shell();show(page);
            if(newReady>prevReady){
                showToast("Antrean: "+newReady+" pesanan berstatus Siap.");
            }else if(newActive>prevActive){
                showToast("Pesanan baru masuk ("+newActive+" antrean aktif).");
            }else if(!backgroundPoll){
                showToast("Data dimuat ("+lastUpdatedTime+" WIB).");
            }
        },error->{if(!backgroundPoll)Ui.error(error.getMessage());});
    }
    private static long countByStates(JsonNode snapshot,Set<String> states){
        if(snapshot==null)return 0;
        return Ui.rows(snapshot,"orders").stream().filter(o->states.contains(o.path("status").asText())).count();
    }
    public void show(String page){
        this.page=page;
        navButtons.forEach((name,button)->{
            if(name.equals(page)){if(!button.getStyleClass().contains("active"))button.getStyleClass().add("active");}
            else button.getStyleClass().remove("active");
        });
        Node content=switch(page){
            case "Dashboard"->dashboard();case "POS"->pos();case "Pesanan"->orders(false);case "Dapur"->orders(true);
            case "Menu"->menus();case "Meja"->tables();
            case "Staf"->staff();case "Pengiriman"->deliveries();case "Ulasan"->reviews();case "Laporan"->reports();
            case "Akun"->account();default->throw new IllegalArgumentException("Halaman tidak dikenal.");};
        root.setCenter(content);
    }
    private void mutation(String action,ObjectNode payload,Consumer<JsonNode> after){
        async.run(()->repository.execute(action,payload),result->{after.accept(result);refresh();},error->Ui.error(error.getMessage()));
    }
    private Node page(String title,String help,Ui.DataTable table,Node... actions){
        FlowPane bar=new FlowPane(8,8,actions);
        VBox p=help==null||help.isBlank()
            ?Ui.page(Ui.heading(title),bar,table.node)
            :Ui.page(Ui.heading(title),Ui.label(help),bar,table.node);
        VBox.setVgrow(table.node,Priority.ALWAYS);
        return p;
    }
    private Node dashboard(){
        return new DashboardView(data,this::orderTable,this::detail).node();
    }
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
        Ui.DataTable t=orderTable(rows);
        t.onRowDoubleClick(row->guard(()->detail(List.of(row))));

        List<Node> actions=new ArrayList<>();
        if(!kitchen){
            ComboBox<String> statusFilter=new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                "Semua status","Antrean aktif","Belum lunas (Pending)","Menunggu","Diproses","Siap","Selesai","Dibatalkan"));
            statusFilter.setValue("Semua status");
            statusFilter.setId("order-status-filter");
            statusFilter.setOnAction(e->{
                String chosen=statusFilter.getValue();
                t.setRowFilter(row->switch(chosen){
                    case "Antrean aktif"->Set.of("Menunggu","Diproses","Siap").contains(row.path("status").asText());
                    case "Belum lunas (Pending)"->"Pending".equals(row.path("payment_status").asText())&&!"Dibatalkan".equals(row.path("status").asText());
                    case "Semua status"->true;
                    default->chosen.equals(row.path("status").asText());
                });
            });
            actions.add(statusFilter);
        }
        actions.add(Ui.button("Rincian",()->guard(()->detail(t.selectedItems()))));
        String[] states=kitchen?new String[]{"Diproses","Siap","Selesai","Dibatalkan"}:new String[]{"Menunggu","Diproses","Siap","Selesai","Dibatalkan"};
        for(String state:states) {
            if(allowed("kitchen")&&!Set.of("Diproses","Siap").contains(state))continue;
            if(allowed("waiter")&&!state.equals("Selesai"))continue;
            Button statusButton=Ui.button(state,()->guard(()->{
                List<JsonNode> selectedOrders=t.selectedItems();
                if(selectedOrders.size()==1){
                    JsonNode order=selectedOrders.get(0);
                    if(!kitchen){
                        applyFreeOrderStatus(order,state);
                        return;
                    }
                    if(Ui.confirm("Ubah pesanan #"+order.path("id").asText()+" menjadi "+state+"?"))
                        async.run(()->repository.execute("order_status",Json.object().put("id",order.path("id").asInt()).put("status",state)),result->refresh(),error->{refresh();Ui.error(error.getMessage()+" Daftar sedang dimuat ulang; periksa status terbaru sebelum mencoba lagi.");});
                    return;
                }
                if(!kitchen){
                    applyFreeOrderStatusBatch(selectedOrders,state);
                    return;
                }
                for(JsonNode order:selectedOrders){
                    String reason=OrderService.statusBlockReason(order,role(),state);
                    if(!reason.isEmpty())throw new IllegalArgumentException("Pesanan #"+order.path("id").asText()+": "+reason);
                }
                if(Ui.confirm("Ubah "+selectedOrders.size()+" pesanan terpilih menjadi "+state+"?")){
                    async.run(()->{
                        JsonNode last=Json.object();
                        for(JsonNode order:selectedOrders){
                            last=repository.execute("order_status",Json.object().put("id",order.path("id").asInt()).put("status",state));
                        }
                        return last;
                    },result->refresh(),error->{refresh();Ui.error(error.getMessage()+" Daftar sedang dimuat ulang; periksa status terbaru sebelum mencoba lagi.");});
                }
            }));
            statusButton.setId("order-status-"+state);
            Runnable update=()->{
                List<JsonNode> selectedList=t.table.getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).toList();
                JsonNode primary=t.table.getSelectionModel().getSelectedItem();
                boolean isCurrentStage=primary!=null&&state.equals(primary.path("status").asText());
                if(isCurrentStage){
                    if(!statusButton.getStyleClass().contains("stage-current"))statusButton.getStyleClass().add("stage-current");
                }else{
                    statusButton.getStyleClass().remove("stage-current");
                }
                if(selectedList.isEmpty()){
                    String reason=OrderService.statusBlockReason(null,role(),state);
                    statusButton.setDisable(true);
                    statusButton.setTooltip(new Tooltip(reason));
                    return;
                }
                if(!kitchen){
                    // Di menu Pesanan, semua tombol tahap bebas dipilih (maju atau mundur bila ada kesalahan) sambil tetap menyorot tahap saat ini
                    statusButton.setDisable(false);
                    statusButton.setTooltip(new Tooltip(isCurrentStage
                        ?"Pesanan saat ini sedang berada di tahap "+state
                        :"Ubah status pesanan menjadi "+state));
                    return;
                }
                String firstReason="";
                for(JsonNode item:selectedList){
                    String reason=OrderService.statusBlockReason(item,role(),state);
                    if(!reason.isEmpty()){firstReason=selectedList.size()>1?"Pesanan #"+item.path("id").asText()+": "+reason:reason;break;}
                }
                statusButton.setDisable(!firstReason.isEmpty());
                statusButton.setTooltip(new Tooltip(firstReason.isEmpty()?"Ubah status menjadi "+state:firstReason));
            };
            t.table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<JsonNode>)c->update.run());
            t.table.getSelectionModel().selectedItemProperty().addListener((obs,old,value)->update.run());
            update.run();actions.add(statusButton);
        }
        if(!kitchen&&allowed("admin","cashier","waiter")){
            Button edit=Ui.button("Edit isi pesanan",()->guard(()->{
                JsonNode order=t.selected();
                if(!"Menunggu".equals(order.path("status").asText())||!"Pending".equals(order.path("payment_status").asText()))
                    throw new IllegalArgumentException("Isi pesanan hanya dapat diedit saat Menunggu dan belum dibayar. Kembalikan status ke Menunggu terlebih dahulu bila perlu mengedit.");
                if(pos!=null&&!pos.cart.isEmpty()&&!Ui.confirm("Ganti keranjang saat ini dengan pesanan ini?"))return;
                pos=null;pos();pos.edit(order);show("POS");
            }));edit.setId("order-edit");
            Runnable updateEdit=()->edit.setDisable(t.table.getSelectionModel().getSelectedItem()==null
                ||!"Menunggu".equals(t.table.getSelectionModel().getSelectedItem().path("status").asText())
                ||!"Pending".equals(t.table.getSelectionModel().getSelectedItem().path("payment_status").asText()));
            t.table.getSelectionModel().selectedItemProperty().addListener((obs,old,value)->updateEdit.run());updateEdit.run();
            edit.setTooltip(new Tooltip("Mengubah item pesanan saat Menunggu. Untuk menerima uang, gunakan Catat pembayaran."));actions.add(edit);
        }
        if(!kitchen&&allowed("admin","cashier")){
            actions.add(paymentButton(t,"order-pay"));
            actions.add(Ui.button("Lihat / cetak nota",()->guard(()->receipt(t.selectedItems()))));
        }
        return page(kitchen?"Kitchen Display":"Pesanan","",t,actions.toArray(Node[]::new));
    }
    private void applyFreeOrderStatus(JsonNode order,String targetState){
        int id=order.path("id").asInt();
        String current=order.path("status").asText("");
        if(current.equals(targetState)){
            showToast("Pesanan #"+id+" saat ini sudah berada di tahap "+current+".");
            return;
        }
        if("Dibatalkan".equals(targetState)){
            if("Lunas".equals(order.path("payment_status").asText())){
                throw new IllegalArgumentException("Pesanan lunas tidak dapat dibatalkan. Refund belum didukung.");
            }
            if(Ui.confirm("Batalkan pesanan #"+id+"?")){
                async.run(()->executeFlexibleStatusChange(id,current,"Dibatalkan"),
                    result->refresh(),error->{refresh();Ui.error(error.getMessage());});
            }
            return;
        }
        if("Selesai".equals(targetState)){
            completeOrderFromAnyStage(order);
            return;
        }
        if(Ui.confirm("Ubah pesanan #"+id+" dari "+current+" menjadi "+targetState+"?")){
            async.run(()->executeFlexibleStatusChange(id,current,targetState),
                result->refresh(),error->{refresh();Ui.error(error.getMessage());});
        }
    }
    private JsonNode executeFlexibleStatusChange(int id,String current,String targetState){
        try{
            return repository.execute("order_status",Json.object().put("id",id).put("status",targetState));
        }catch(RuntimeException ex){
            List<String> forwardSteps=forwardTransitionSteps(current,targetState);
            if(!forwardSteps.isEmpty()){
                JsonNode last=Json.object();
                for(String step:forwardSteps){
                    last=repository.execute("order_status",Json.object().put("id",id).put("status",step));
                }
                return last;
            }
            throw new IllegalStateException(ex.getMessage()+" (Untuk mengembalikan status mundur seperti Selesai → Diproses, pastikan migrasi database 007_flexible_order_status.sql sudah dijalankan di Supabase.)",ex);
        }
    }
    private void applyFreeOrderStatusBatch(List<JsonNode> selectedOrders,String targetState){
        if("Selesai".equals(targetState)){
            completeMultipleOrdersFromAnyStage(selectedOrders);
            return;
        }
        if("Dibatalkan".equals(targetState)){
            for(JsonNode order:selectedOrders){
                if("Lunas".equals(order.path("payment_status").asText()))
                    throw new IllegalArgumentException("Pesanan #"+order.path("id").asText()+" sudah Lunas dan tidak dapat dibatalkan.");
            }
        }
        if(Ui.confirm("Ubah "+selectedOrders.size()+" pesanan terpilih menjadi "+targetState+"?")){
            async.run(()->{
                JsonNode last=Json.object();
                for(JsonNode order:selectedOrders){
                    int id=order.path("id").asInt();
                    String current=order.path("status").asText("");
                    if(current.equals(targetState))continue;
                    last=executeFlexibleStatusChange(id,current,targetState);
                }
                return last;
            },result->refresh(),error->{refresh();Ui.error(error.getMessage());});
        }
    }
    private static List<String> forwardTransitionSteps(String current,String target){
        List<String> order=List.of("Menunggu","Diproses","Siap","Selesai");
        int fromIdx=order.indexOf(current);
        int toIdx=order.indexOf(target);
        if(fromIdx<0||toIdx<=fromIdx)return List.of();
        return order.subList(fromIdx+1,toIdx+1);
    }
    private void completeOrderFromAnyStage(JsonNode order){
        String current=order.path("status").asText("");
        if(Set.of("Selesai","Dibatalkan").contains(current))
            throw new IllegalArgumentException("Pesanan sudah berstatus "+current+".");
        boolean paid="Lunas".equals(order.path("payment_status").asText());
        if(!paid){
            if(!allowed("admin","cashier"))
                throw new IllegalArgumentException("Pesanan belum lunas. Minta kasir atau admin mencatat pembayaran terlebih dahulu.");
            pay(order,true);
            return;
        }
        finishPaidOrderFromAnyStage(order,true);
    }
    private void completeMultipleOrdersFromAnyStage(List<JsonNode> selectedOrders){
        for(JsonNode order:selectedOrders){
            String current=order.path("status").asText("");
            if(Set.of("Selesai","Dibatalkan").contains(current))
                throw new IllegalArgumentException("Pesanan #"+order.path("id").asText()+" sudah berstatus "+current+".");
            if(!"Lunas".equals(order.path("payment_status").asText()))
                throw new IllegalArgumentException("Pesanan #"+order.path("id").asText()+" belum lunas. Untuk penyelesaian massal, pastikan pembayaran sudah Lunas atau pilih satu per satu untuk mencatat pembayaran.");
            String delivery=order.path("delivery_status").asText("");
            if("delivery".equals(order.path("type").asText())&&!delivery.isBlank()&&!"Terkirim".equals(delivery)&&order.path("courier_id").asInt(0)<=0)
                throw new IllegalArgumentException("Pesanan delivery #"+order.path("id").asText()+" belum memiliki kurir. Tugaskan kurir terlebih dahulu atau pilih pesanan tersebut secara tunggal.");
        }
        if(!Ui.confirm("Selesaikan "+selectedOrders.size()+" pesanan terpilih?"))return;
        async.run(()->{
            JsonNode last=Json.object();
            for(JsonNode order:selectedOrders){
                last=executeCompletePaidOrder(order);
            }
            return last;
        },result->refresh(),error->{refresh();Ui.error(error.getMessage());});
    }
    private JsonNode executeCompletePaidOrder(JsonNode order){
        int id=order.path("id").asInt();
        String current=order.path("status").asText("");
        for(String step:forwardTransitionSteps(current,"Siap")){
            repository.execute("order_status",Json.object().put("id",id).put("status",step));
        }
        String delivery=order.path("delivery_status").asText("");
        if("delivery".equals(order.path("type").asText())&&!delivery.isBlank()&&!"Terkirim".equals(delivery)){
            if("Ditugaskan".equals(delivery))repository.execute("delivery_status",Json.object().put("id",id).put("status","Diambil"));
            return repository.execute("delivery_status",Json.object().put("id",id).put("status","Terkirim"));
        }
        return repository.execute("order_status",Json.object().put("id",id).put("status","Selesai"));
    }
    private Button paymentButton(Ui.DataTable table,String id){
        Button button=Ui.button("Catat pembayaran",()->guard(()->pay(table.selected(),false)));button.setId(id);
        Runnable update=()->{JsonNode selected=table.table.getSelectionModel().getSelectedItem();button.setDisable(selected==null||!"Pending".equals(selected.path("payment_status").asText())||"Dibatalkan".equals(selected.path("status").asText()));};
        table.table.getSelectionModel().selectedItemProperty().addListener((obs,old,value)->update.run());update.run();
        button.setTooltip(new Tooltip("Pilih pesanan dengan pembayaran Pending yang tidak dibatalkan. Pembayaran lunas tidak dicatat ulang."));return button;
    }
    private void completeReadyOrder(JsonNode order){
        completeOrderFromAnyStage(order);
    }
    private void finishPaidOrderFromAnyStage(JsonNode order,boolean askConfirm){
        int id=order.path("id").asInt();
        String delivery=order.path("delivery_status").asText("");
        boolean isIncompleteDelivery="delivery".equals(order.path("type").asText())&&!delivery.isBlank()&&!"Terkirim".equals(delivery);
        if(isIncompleteDelivery){
            int courierId=order.path("courier_id").asInt(0);
            if(courierId<=0||"Menunggu".equals(delivery)){
                if(!allowed("admin","cashier"))
                    throw new IllegalArgumentException("Kurir belum ditugaskan untuk pengiriman ini.");
                ComboBox<Ui.Choice> courier=courierChoices();
                if(!courier.getItems().isEmpty())courier.setValue(courier.getItems().get(0));
                editor("Selesaikan pengiriman & pesanan #"+id,
                    new VBox(12,Ui.label("Pesanan ini berjenis delivery dan belum memiliki kurir. Pilih kurir untuk mengonfirmasi pengiriman Terkirim dan menyelesaikan pesanan."),courier),
                    ()->{
                        Integer selectedCourier=Ui.integerChoice(courier);
                        if(selectedCourier==null)throw new IllegalArgumentException("Pilih kurir aktif.");
                        return Json.object().put("id",id).put("courier_id",selectedCourier);
                    },
                    body->{
                        for(String step:forwardTransitionSteps(order.path("status").asText(""),"Siap")){
                            repository.execute("order_status",Json.object().put("id",id).put("status",step));
                        }
                        repository.execute("assign_delivery",body);
                        repository.execute("delivery_status",Json.object().put("id",id).put("status","Diambil"));
                        return repository.execute("delivery_status",Json.object().put("id",id).put("status","Terkirim"));
                    },
                    ()->{});
                return;
            }
            if(askConfirm&&!Ui.confirm("Ubah pesanan delivery #"+id+" menjadi Selesai (sekaligus konfirmasi pengiriman Terkirim)?"))return;
            async.run(()->executeCompletePaidOrder(order),result->refresh(),error->{refresh();Ui.error(error.getMessage());});
            return;
        }
        if(askConfirm&&!Ui.confirm("Ubah pesanan #"+id+" menjadi Selesai?"))return;
        async.run(()->executeCompletePaidOrder(order),
            result->refresh(),error->{refresh();Ui.error(error.getMessage()+" Daftar sedang dimuat ulang; periksa status terbaru sebelum mencoba lagi.");});
    }
    private void detail(List<JsonNode> orders){
        Dialog<Void> d=new Dialog<>();Ui.styleDialog(d);d.initOwner(stage);
        d.setTitle(orders.size()==1?"Pesanan #"+orders.get(0).path("id").asText():"Rincian "+orders.size()+" pesanan terpilih");
        List<String> blocks=new ArrayList<>();
        for(JsonNode order:orders){
            blocks.add("Pesanan #"+order.path("id").asText()+" ("+Ui.text(order,"queue")+")\nWaktu: "+Ui.time(order.path("time"))+" WIB\nStatus: "+Ui.text(order,"status")+"\nPembayaran: "+Ui.text(order,"payment_status")+"\nMeja: "+Ui.text(order,"table_name")
                +"\nJenis: "+Ui.text(order,"type")+"\nCatatan: "+Ui.text(order,"notes")+"\nAlamat: "+Ui.text(order,"address")+"\nItem: "+itemSummary(order));
        }
        TextArea text=new TextArea(String.join("\n\n----------------------------------------\n\n",blocks));
        text.setEditable(false);text.setWrapText(true);text.setPrefSize(650,380);d.getDialogPane().setContent(text);d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);d.showAndWait();
    }
    private Node menus(){
        Ui.DataTable t=new Ui.DataTable(Ui.rows(data,"menu"),Ui.col("ID","id_menu"),Ui.col("Menu","nama_menu"),Ui.col("Kategori","category"),
            new Ui.Column("Harga",n->Ui.money(n.path("harga_satuan").asLong())),new Ui.Column("Aktif",n->n.path("is_active").asBoolean()?"Ya":"Diarsipkan"),Ui.col("Deskripsi","deskripsi_menu"));
        ComboBox<String> filter=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Semua kategori","Makanan","Minuman"));filter.setValue("Semua kategori");filter.setAccessibleText("Filter kategori menu");
        filter.setOnAction(e->t.setRowFilter(row->"Semua kategori".equals(filter.getValue())||filter.getValue().equals(row.path("category").asText())));
        return page("Menu","",t,filter,
            adminButton("Tambah menu",()->editMenu(null)),adminButton("Edit / arsipkan",()->editMenus(t.selectedItems())));
    }
    private Node adminButton(String title,Runnable action){Button b=Ui.button(title,()->guard(action));b.setVisible(allowed("admin"));b.setManaged(b.isVisible());return b;}
    private void editMenus(List<JsonNode> rows){
        if(rows.size()==1){editMenu(rows.get(0));return;}
        ComboBox<String> category=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Tetap (tidak diubah)","Makanan","Minuman"));
        category.setValue("Tetap (tidak diubah)");
        ComboBox<String> activeChoice=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Tetap (tidak diubah)","Aktifkan semua","Arsipkan semua"));
        activeChoice.setValue("Tetap (tidak diubah)");
        GridPane f=Ui.form();
        Ui.field(f,0,"Jumlah dipilih",Ui.label(rows.size()+" menu"));
        Ui.field(f,1,"Ubah kategori",category);
        Ui.field(f,2,"Status aktif",activeChoice);
        editor("Edit "+rows.size()+" menu terpilih",f,()->{
            if("Tetap (tidak diubah)".equals(category.getValue())&&"Tetap (tidak diubah)".equals(activeChoice.getValue()))
                throw new IllegalArgumentException("Pilih perubahan kategori atau status aktif.");
            return Json.object().put("category",category.getValue()).put("active_mode",activeChoice.getValue());
        },body->{
            JsonNode last=Json.object();
            String cat=body.path("category").asText();
            String act=body.path("active_mode").asText();
            for(JsonNode row:rows){
                String targetCat="Tetap (tidak diubah)".equals(cat)?row.path("category").asText("Makanan"):cat;
                boolean targetActive="Tetap (tidak diubah)".equals(act)?row.path("is_active").asBoolean():"Aktifkan semua".equals(act);
                ObjectNode p=Json.object()
                    .put("id",row.path("id_menu").asInt())
                    .put("name",row.path("nama_menu").asText())
                    .put("price",row.path("harga_satuan").asLong())
                    .put("category",targetCat)
                    .put("description",row.path("deskripsi_menu").asText(""))
                    .put("active",targetActive);
                last=repository.execute("save_menu",p);
            }
            return last;
        },()->{});
    }
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
        return new TablesView(stage,data,new TablesView.TableCallbacks(){
            public boolean isAdmin(){return allowed("admin");}
            public boolean canManageOrders(){return allowed("admin","cashier","waiter");}
            public boolean canAcceptPayment(){return allowed("admin","cashier");}
            public void openNewTableDialog(){guard(()->editTable(null));}
            public void saveTable(ObjectNode payload,Runnable onSuccess,Consumer<Throwable> onError){
                async.run(()->repository.execute("save_table",payload),result->{onSuccess.run();refresh();},onError);
            }
            public void startOrderAtTable(String tableId){
                show("POS");
                pos.type.setValue("dine_in");
                Ui.select(pos.table,tableId);
            }
            public void showOrderDetail(JsonNode order){guard(()->detail(List.of(order)));}
            public void editWaitingOrder(JsonNode order){
                guard(()->{
                    if(pos!=null&&!pos.cart.isEmpty()&&!Ui.confirm("Ganti keranjang saat ini dengan pesanan ini?"))return;
                    pos=null;pos();pos.edit(order);show("POS");
                });
            }
            public void recordPayment(JsonNode order){guard(()->pay(order,false));}
            public void completeReadyOrder(JsonNode order){guard(()->Workspace.this.completeReadyOrder(order));}
        }).node();
    }
    private void editTable(JsonNode row){
        TextField name=new TextField(value(row,"nomor_meja")),qr=new TextField(value(row,"qr_meja"));CheckBox active=active(row);
        GridPane f=Ui.form();Ui.field(f,0,"Nama meja",name);Ui.field(f,1,"Kode QR",qr);Ui.field(f,2,"Aktif",active);
        editor("Tambah Meja",f,"save_table",()->{ObjectNode p=Json.object().put("name",required(name)).put("qr",required(qr)).put("active",active.isSelected());if(row!=null)p.put("id",row.path("id_customer").asInt());return p;});
    }
    private void pay(JsonNode order,boolean completeAfterPay){
        if(!"Pending".equals(order.path("payment_status").asText())||"Dibatalkan".equals(order.path("status").asText()))throw new IllegalArgumentException("Pembayaran tidak dapat diproses ulang.");
        long totalBill=order.path("total").asLong();
        ComboBox<String> method=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Cash","Debit","QRIS"));method.setValue("Cash");
        method.setId("payment-method");
        TextField received=new TextField(String.valueOf(totalBill));Label change=Ui.label("");
        received.setId("payment-received");
        Button exactBtn=Ui.button("Uang pas",()->received.setText(String.valueOf(totalBill)));
        Button b50k=Ui.button("Rp50.000",()->received.setText(String.valueOf(Math.max(50_000L,totalBill))));
        Button b100k=Ui.button("Rp100.000",()->received.setText(String.valueOf(Math.max(100_000L,totalBill))));
        Button plus10k=Ui.button("+Rp10.000",()->{
            long currentVal;
            try{currentVal=Ui.amount(received);}catch(RuntimeException ex){currentVal=totalBill;}
            received.setText(String.valueOf(Math.addExact(currentVal,10_000L)));
        });
        Button plus50k=Ui.button("+Rp50.000",()->{
            long currentVal;
            try{currentVal=Ui.amount(received);}catch(RuntimeException ex){currentVal=totalBill;}
            received.setText(String.valueOf(Math.addExact(currentVal,50_000L)));
        });
        FlowPane quickCashBar=new FlowPane(6,6,exactBtn,b50k,b100k,plus10k,plus50k);
        Runnable compute=()->{
            try{
                PaymentStrategy strategy=PaymentStrategy.forMethod(method.getValue());
                long entered=Ui.amount(received);
                long kembalian=strategy.validateAndCalculateChange(totalBill,entered);
                change.setText("Diterima: "+Ui.money(entered)+" · Kembali: "+Ui.money(kembalian));
            }catch(RuntimeException ex){
                change.setText(ex.getMessage());
            }
        };
        received.textProperty().addListener((obs,a,b)->compute.run());compute.run();
        method.setOnAction(e->{
            PaymentStrategy strategy=PaymentStrategy.forMethod(method.getValue());
            received.setText(String.valueOf(strategy.defaultReceivedAmount(totalBill)));
            received.setDisable(!strategy.allowsCustomAmount());
            quickCashBar.setDisable(!strategy.allowsCustomAmount());
        });
        VBox receivedBox=new VBox(6,received,quickCashBar);
        GridPane f=Ui.form();Ui.field(f,0,"Tagihan",Ui.label(Ui.money(totalBill)));Ui.field(f,1,"Metode",method);Ui.field(f,2,"Diterima",receivedBox);Ui.field(f,3,"Kembalian",change);
        String promptText=completeAfterPay
            ?"Pesanan #"+order.path("id").asText()+" berstatus Siap namun belum lunas. Konfirmasi pembayaran di bawah untuk sekaligus mengubah status pesanan menjadi Selesai."
            :"Simpan hanya setelah uang diterima atau bukti pembayaran non-tunai diverifikasi. Konfirmasi ini bersifat manual.";
        VBox body=new VBox(14,Ui.label(promptText),f);
        editor((completeAfterPay?"Pembayaran & selesaikan pesanan #":"Konfirmasi pembayaran #")+order.path("id").asText(),body,()->{
            PaymentStrategy strategy=PaymentStrategy.forMethod(method.getValue());
            long amount=Ui.amount(received);
            strategy.validateAndCalculateChange(totalBill,amount);
            if(!Ui.confirm("Konfirmasi bahwa pembayaran sudah benar-benar diterima?"))return null;
            return Json.object().put("id",order.path("id").asInt()).put("method",strategy.method()).put("received",amount);
        },payload->{
            JsonNode payResult=repository.execute("pay",payload);
            if(completeAfterPay){
                int id=order.path("id").asInt();
                for(String step:forwardTransitionSteps(order.path("status").asText(""),"Siap")){
                    repository.execute("order_status",Json.object().put("id",id).put("status",step));
                }
                String delivery=order.path("delivery_status").asText("");
                boolean isIncompleteDelivery="delivery".equals(order.path("type").asText())&&!delivery.isBlank()&&!"Terkirim".equals(delivery);
                if(isIncompleteDelivery){
                    int courierId=order.path("courier_id").asInt(0);
                    if(courierId>0){
                        if("Ditugaskan".equals(delivery))repository.execute("delivery_status",Json.object().put("id",id).put("status","Diambil"));
                        return repository.execute("delivery_status",Json.object().put("id",id).put("status","Terkirim"));
                    }
                }else{
                    return repository.execute("order_status",Json.object().put("id",id).put("status","Selesai"));
                }
            }
            return payResult;
        },()->{
            if(completeAfterPay&&"delivery".equals(order.path("type").asText())&&order.path("courier_id").asInt(0)<=0){
                ObjectNode paidOrder=order.deepCopy();
                paidOrder.put("payment_status","Lunas");
                javafx.application.Platform.runLater(()->guard(()->finishPaidOrderFromAnyStage(paidOrder,false)));
            }
        });
    }
    private void receipt(List<JsonNode> orders){
        List<String> rendered=orders.stream().map(o->ReceiptService.render(o,data.path("settings"))).toList();
        String text=String.join("\n\n========================================\n\n",rendered);
        String titleSuffix=orders.size()==1?"#"+orders.get(0).path("id").asText():orders.size()+" pesanan";
        String fileSuffix=orders.size()==1?orders.get(0).path("id").asText():"gabungan-"+orders.size();
        TextArea preview=new TextArea(text);preview.setEditable(false);preview.setWrapText(true);preview.setStyle("-fx-font-family: 'Consolas';");preview.setPrefSize(650,500);
        Dialog<Void> d=new Dialog<>();Ui.styleDialog(d);d.initOwner(stage);d.setTitle("Nota "+titleSuffix);
        d.getDialogPane().setContent(new VBox(12,preview,Ui.button("Cetak",()->print(text)),Ui.button("Simpan TXT",()->saveText("nota-"+fileSuffix+".txt",text))));
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
        Ui.DataTable staff=new Ui.DataTable(Ui.rows(data,"staff"),Ui.col("ID","id_staff"),new Ui.Column("Nama / peran",n->Ui.staff(n).displayLabel()),new Ui.Column("Akun",n->staffAccountLabel("id_staff",n.path("id_staff").asText())),new Ui.Column("Penugasan aktif dimuat",n->""+workload(n)),new Ui.Column("Aktif",n->""+n.path("is_active").asBoolean()));
        Ui.DataTable waiters=new Ui.DataTable(Ui.rows(data,"waiters"),Ui.col("ID pelayan","id_pelayan"),Ui.col("Nama depan","nama_depan"),Ui.col("Nama belakang","nama_belakang"),new Ui.Column("Akun",n->staffAccountLabel("id_pelayan",n.path("id_pelayan").asText())),new Ui.Column("Aktif",n->""+n.path("is_active").asBoolean()));
        TabPane tabs=new TabPane();Tab a=new Tab("Dapur & kurir",new VBox(12,new FlowPane(10,10,adminButton("Tambah staf",()->editStaff(null,false)),adminButton("Edit staf",()->editStaff(staff.selected(),false)),adminButton("Buat akun staf",()->createAccountForIdentity(staff.selected(),false))),staff.node));
        Tab b=new Tab("Pelayan",new VBox(12,new FlowPane(10,10,adminButton("Tambah pelayan",()->editStaff(null,true)),adminButton("Edit pelayan",()->editStaff(waiters.selected(),true)),adminButton("Buat akun pelayan",()->createAccountForIdentity(waiters.selected(),true))),waiters.node));a.setClosable(false);b.setClosable(false);tabs.getTabs().addAll(a,b);
        VBox body=Ui.page(Ui.heading("Staf"),tabs);VBox.setVgrow(tabs,Priority.ALWAYS);return body;
    }
    private String staffAccountLabel(String idKey,String id){
        return Ui.rows(data,"profiles").stream().filter(p->p.path(idKey).asText().equals(id))
            .map(p->Ui.text(p,"display_name")+" ("+(p.path("is_active").asBoolean()?"Aktif":"Nonaktif")+")")
            .findFirst().orElse("Belum ada akun");
    }
    private void createAccountForIdentity(JsonNode row,boolean waiter){
        if(!row.path("is_active").asBoolean())throw new IllegalArgumentException("Aktifkan identitas staf/pelayan terlebih dahulu sebelum membuat akun.");
        String idKey=waiter?"id_pelayan":"id_staff";
        String id=row.path(idKey).asText();
        if(Ui.rows(data,"profiles").stream().anyMatch(p->p.path(idKey).asText().equals(id)))
            throw new IllegalArgumentException("Identitas ini sudah terhubung ke akun pengguna.");
        String targetRole=waiter?"waiter":row.path("kind").asText("kitchen");
        String displayName=waiter?(Ui.text(row,"nama_depan")+" "+Ui.text(row,"nama_belakang")).trim():Ui.text(row,"nama_staff");
        createAccount(targetRole,id,displayName);
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
            List<JsonNode> selectedOrders=t.selectedItems();ComboBox<Ui.Choice> courier=courierChoices();
            String labelText=selectedOrders.size()==1?"Pilih kurir aktif. Riwayat tidak dipetakan ke kurir secara otomatis.":"Pilih kurir aktif untuk "+selectedOrders.size()+" pesanan pengiriman terpilih.";
            editor("Tugaskan kurir",new VBox(12,Ui.label(labelText),courier),()->{
                Integer id=Ui.integerChoice(courier);if(id==null)throw new IllegalArgumentException("Pilih kurir.");
                return Json.object().put("courier_id",id);
            },body->{
                int courierId=body.path("courier_id").asInt();
                JsonNode last=Json.object();
                for(JsonNode order:selectedOrders){
                    last=repository.execute("assign_delivery",Json.object().put("id",order.path("id").asInt()).put("courier_id",courierId));
                }
                return last;
            },()->{});
        })));
        for(String state:new String[]{"Diambil","Terkirim"})actions.add(Ui.button(state,()->guard(()->{
            List<JsonNode> selectedOrders=t.selectedItems();
            String prompt=selectedOrders.size()==1?"Konfirmasi pengiriman #"+selectedOrders.get(0).path("id").asText()+" "+state+"?":"Konfirmasi "+selectedOrders.size()+" pengiriman terpilih menjadi "+state+"?";
            if(Ui.confirm(prompt)){
                async.run(()->{
                    JsonNode last=Json.object();
                    for(JsonNode order:selectedOrders){
                        last=repository.execute("delivery_status",Json.object().put("id",order.path("id").asInt()).put("status",state));
                    }
                    return last;
                },result->refresh(),error->{refresh();Ui.error(error.getMessage());});
            }
        })));
        return page("Pengiriman","",t,actions.toArray(Node[]::new));
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
        return page("Ulasan",String.format(Locale.forLanguageTag("id-ID"),"Rata-rata %.2f dari %d ulasan · %s",avg,rows.size(),distribution.trim()),t,add);
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
        return page("Laporan & rekonsiliasi",methods,t,
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
    private VBox userAccessSection(){
        Ui.DataTable profiles=new Ui.DataTable(Ui.rows(data,"profiles"),Ui.col("Nama","display_name"),Ui.col("Peran","role"),Ui.col("User UUID","id"),Ui.col("Staf","id_staff"),Ui.col("Pelayan","id_pelayan"),new Ui.Column("Aktif",n->""+n.path("is_active").asBoolean()));
        VBox box=new VBox(12,Ui.heading("Hak akses pengguna"),
            new FlowPane(10,10,Ui.button("Tambah akun",()->createAccount("cashier",null,"")),
                Ui.button("Hubungkan akun yang sudah ada",()->editProfile(null)),
                Ui.button("Edit profil",()->guard(()->editProfile(profiles.selected())))),profiles.node);
        VBox.setVgrow(profiles.node,Priority.ALWAYS);
        return box;
    }
    private static final String NEW_IDENTITY_ID = "__new__";
    private void createAccount() {
        createAccount("cashier", null, "");
    }
    private void createAccount(String initialRole, String initialIdentityId, String initialName) {
        TextField email = new TextField();
        email.setId("account-email");
        TextField name = new TextField(initialName == null ? "" : initialName);
        name.setId("account-name");
        ComboBox<String> role = new ComboBox<>();
        role.setId("account-role");
        role.getItems().addAll("admin", "cashier", "kitchen", "waiter", "courier");
        ComboBox<Ui.Choice> identity = new ComboBox<>();
        identity.setId("account-identity");
        identity.setMaxWidth(Double.MAX_VALUE);
        TextField waiterIdField = new TextField(nextWaiterId());
        waiterIdField.setId("account-waiter-id");
        PasswordField password = new PasswordField();
        password.setId("account-password");
        PasswordField confirmation = new PasswordField();
        confirmation.setId("account-password-confirm");

        GridPane fields = Ui.form();
        Ui.field(fields, 0, "Peran", role);
        Ui.field(fields, 1, "Identitas staf", identity);
        Ui.field(fields, 2, "ID pelayan baru", waiterIdField);
        Ui.field(fields, 3, "Nama tampilan", name);
        Ui.field(fields, 4, "Email staf", email);
        Ui.field(fields, 5, "Kata sandi awal", Ui.passwordBox(password));
        Ui.field(fields, 6, "Ulangi kata sandi", Ui.passwordBox(confirmation));

        Map<String, String> identityNames = new HashMap<>();
        String[] lastAutoFilledName = {initialName == null ? "" : initialName};
        Integer[] createdStaffId = {null};
        String[] createdStaffRole = {null};
        String[] createdWaiterId = {null};

        Runnable updateWaiterIdState = () -> {
            boolean isNewWaiter = "waiter".equals(role.getValue())
                    && identity.getValue() != null
                    && NEW_IDENTITY_ID.equals(identity.getValue().id());
            waiterIdField.setDisable(!isNewWaiter);
            if (isNewWaiter && waiterIdField.getText().isBlank()) {
                waiterIdField.setText(nextWaiterId());
            }
        };

        identity.valueProperty().addListener((obs, oldChoice, newChoice) -> {
            updateWaiterIdState.run();
            if (newChoice == null) return;
            if (!NEW_IDENTITY_ID.equals(newChoice.id())) {
                String mappedName = identityNames.getOrDefault(newChoice.id(), "");
                if (!mappedName.isBlank()) {
                    name.setText(mappedName);
                    lastAutoFilledName[0] = mappedName;
                }
                if ("waiter".equals(role.getValue())) {
                    waiterIdField.setText(newChoice.id());
                }
            } else {
                if (name.getText().trim().equals(lastAutoFilledName[0])) {
                    name.clear();
                    lastAutoFilledName[0] = "";
                }
                if ("waiter".equals(role.getValue())) {
                    waiterIdField.setText(createdWaiterId[0] != null ? createdWaiterId[0] : nextWaiterId());
                }
            }
        });

        role.valueProperty().addListener((observable, previous, current) -> {
            if (current == null) return;
            boolean waiter = current.equals("waiter");
            boolean assigned = waiter || current.equals("kitchen") || current.equals("courier");
            identity.getItems().clear();
            identityNames.clear();
            identity.setValue(null);
            identity.setDisable(!assigned);
            if (assigned) {
                String roleLabel = waiter ? "pelayan" : (current.equals("kitchen") ? "staf dapur" : "kurir");
                Ui.Choice createNewChoice = new Ui.Choice(NEW_IDENTITY_ID, "+ Buat identitas " + roleLabel + " baru");
                identity.getItems().add(createNewChoice);
                populateAvailableIdentities(current, null, identity, identityNames);
                identity.setValue(createNewChoice);
            } else {
                if (name.getText().trim().equals(lastAutoFilledName[0])) {
                    name.clear();
                    lastAutoFilledName[0] = "";
                }
            }
            identity.setPromptText(assigned ? "Pilih atau buat identitas" : "Tidak memerlukan identitas staf");
            updateWaiterIdState.run();
        });

        role.setValue(initialRole != null && role.getItems().contains(initialRole) ? initialRole : "cashier");
        if (initialIdentityId != null && !initialIdentityId.isBlank()) {
            Ui.select(identity, initialIdentityId);
        }
        if (initialName != null && !initialName.isBlank()) {
            name.setText(initialName);
            lastAutoFilledName[0] = initialName;
        }

        VBox contents = new VBox(10, fields);
        editor("Tambah akun", contents, () -> {
            String address = required(email).toLowerCase(Locale.ROOT);
            if (!address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || address.length() > 254) {
                throw new IllegalArgumentException("Masukkan alamat email staf yang valid.");
            }
            String displayName = required(name);
            if (displayName.codePointCount(0, displayName.length()) > 100) {
                throw new IllegalArgumentException("Nama maksimal 100 karakter.");
            }
            String selectedRole = role.getValue();
            ObjectNode profile = Json.object().put("email", address).put("name", displayName).put("role", selectedRole);
            if (!identity.isDisabled()) {
                Ui.Choice selectedIdentity = identity.getValue();
                if (selectedIdentity == null) throw new IllegalArgumentException("Pilih identitas staf aktif atau buat identitas baru.");
                if (NEW_IDENTITY_ID.equals(selectedIdentity.id())) {
                    splitPersonName(displayName);
                    profile.put("create_identity", true);
                    if ("waiter".equals(selectedRole)) {
                        String newWaiterId = validateNewWaiterId(required(waiterIdField), createdWaiterId[0]);
                        profile.put("waiter_id", newWaiterId);
                    }
                } else if ("waiter".equals(selectedRole)) {
                    profile.put("waiter_id", selectedIdentity.id());
                } else {
                    profile.put("staff_id", Integer.parseInt(selectedIdentity.id()));
                }
            }
            profile.put("password", confirmedPassword(password, confirmation));
            return profile;
        }, body -> {
            ObjectNode requestBody = body.deepCopy();
            String selectedRole = requestBody.path("role").asText();
            boolean createNew = requestBody.path("create_identity").asBoolean(false);
            requestBody.remove("create_identity");
            if (createNew) {
                String[] parts = splitPersonName(requestBody.path("name").asText());
                if ("kitchen".equals(selectedRole) || "courier".equals(selectedRole)) {
                    if (createdStaffId[0] == null || !selectedRole.equals(createdStaffRole[0])) {
                        JsonNode saved = repository.execute("save_staff", Json.object()
                                .put("first", parts[0]).put("last", parts[1])
                                .put("kind", selectedRole).put("active", true));
                        createdStaffId[0] = saved.path("id").asInt();
                        createdStaffRole[0] = selectedRole;
                    } else {
                        repository.execute("save_staff", Json.object()
                                .put("id", createdStaffId[0])
                                .put("first", parts[0]).put("last", parts[1])
                                .put("kind", selectedRole).put("active", true));
                    }
                    requestBody.put("staff_id", createdStaffId[0]);
                } else if ("waiter".equals(selectedRole)) {
                    String targetWaiterId = requestBody.path("waiter_id").asText();
                    boolean isNewRow = createdWaiterId[0] == null || !createdWaiterId[0].equals(targetWaiterId);
                    repository.execute("save_waiter", Json.object()
                            .put("waiter_id", targetWaiterId)
                            .put("first", parts[0]).put("last", parts[1])
                            .put("active", true).put("is_new", isNewRow));
                    createdWaiterId[0] = targetWaiterId;
                    requestBody.put("waiter_id", targetWaiterId);
                }
            }
            return repository.createStaffAccount(requestBody);
        }, () -> {
            password.clear();
            confirmation.clear();
            accountNotice("Akun dibuat. Staf dapat masuk dengan email dan kata sandi awal.");
        });
        if (createdStaffId[0] != null || createdWaiterId[0] != null) {
            refresh();
        }
    }
    private void populateAvailableIdentities(String selectedRole, String allowProfileId, ComboBox<Ui.Choice> identity, Map<String, String> identityNames) {
        boolean waiter = "waiter".equals(selectedRole);
        String idKey = waiter ? "id_pelayan" : "id_staff";
        List<JsonNode> profiles = Ui.rows(data, "profiles");
        Set<String> couriers = new HashSet<>();
        Ui.rows(data, "couriers").forEach(row -> couriers.add(row.path("id_staff").asText()));
        for (JsonNode row : Ui.rows(data, waiter ? "waiters" : "staff")) {
            String id = row.path(idKey).asText();
            boolean matches = waiter || ("kitchen".equals(selectedRole) ? "kitchen".equals(row.path("kind").asText()) : couriers.contains(id));
            boolean available = profiles.stream().noneMatch(profile ->
                    profile.path(idKey).asText().equals(id)
                    && (allowProfileId == null || !profile.path("id").asText().equals(allowProfileId)));
            if (matches && row.path("is_active").asBoolean() && available) {
                String caption = waiter
                        ? (Ui.text(row, "nama_depan") + " " + Ui.text(row, "nama_belakang")).trim()
                        : Ui.text(row, "nama_staff");
                identityNames.put(id, caption);
                identity.getItems().add(new Ui.Choice(id, caption + " · " + id));
            }
        }
    }
    private String nextWaiterId() {
        int max = 0;
        for (JsonNode row : Ui.rows(data, "waiters")) {
            String id = row.path("id_pelayan").asText("");
            if (id.matches("PLY-\\d+")) {
                try { max = Math.max(max, Integer.parseInt(id.substring(4))); }
                catch (NumberFormatException ignored) {}
            }
        }
        return String.format(Locale.ROOT, "PLY-%02d", max + 1);
    }
    private String validateNewWaiterId(String waiterId, String alreadyCreatedId) {
        String cleaned = waiterId.trim();
        if (cleaned.isEmpty() || cleaned.length() > 10) {
            throw new IllegalArgumentException("ID pelayan wajib diisi, maksimal 10 karakter.");
        }
        boolean existsInWaiters = Ui.rows(data, "waiters").stream()
                .anyMatch(w -> w.path("id_pelayan").asText().equalsIgnoreCase(cleaned));
        if (existsInWaiters && (alreadyCreatedId == null || !alreadyCreatedId.equalsIgnoreCase(cleaned))) {
            throw new IllegalArgumentException("ID pelayan sudah digunakan. Gunakan ID pelayan lain atau pilih pelayan dari daftar.");
        }
        return cleaned;
    }
    static String[] splitPersonName(String fullName) {
        String cleaned = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) throw new IllegalArgumentException("Nama tampilan wajib diisi.");
        int space = cleaned.indexOf(' ');
        String first = space < 0 ? cleaned : cleaned.substring(0, space).trim();
        String last = space < 0 ? "-" : cleaned.substring(space + 1).trim();
        if (last.isEmpty()) last = "-";
        if (first.length() > 25) {
            throw new IllegalArgumentException("Nama depan maksimal 25 karakter.");
        }
        if (last.length() > 25) {
            last = last.substring(0, 25).trim();
        }
        return new String[]{first, last};
    }
    private void editProfile(JsonNode row){
        String currentProfileId = row == null ? null : row.path("id").asText();
        if (currentProfileId != null && currentProfileId.equals(data.path("profile").path("id").asText())) {
            throw new IllegalArgumentException("Perubahan profil sendiri dilakukan admin lain agar akses admin tidak terkunci.");
        }
        TextField uuid=new TextField(value(row,"id")),name=new TextField(value(row,"display_name"));uuid.setDisable(row!=null);
        ComboBox<String> role=new ComboBox<>(javafx.collections.FXCollections.observableArrayList("admin","cashier","kitchen","waiter","courier"));
        ComboBox<Ui.Choice> identity=new ComboBox<>();identity.setMaxWidth(Double.MAX_VALUE);
        TextField waiterIdField=new TextField(nextWaiterId());
        Map<String,String> identityNames=new HashMap<>();
        String[] lastAutoFilledName={value(row,"display_name")};
        Integer[] createdStaffId={null};
        String[] createdStaffRole={null};
        String[] createdWaiterId={null};

        Runnable updateWaiterField=()->{
            boolean isNewWaiter="waiter".equals(role.getValue())&&identity.getValue()!=null&&NEW_IDENTITY_ID.equals(identity.getValue().id());
            waiterIdField.setDisable(!isNewWaiter);
            if(isNewWaiter&&waiterIdField.getText().isBlank())waiterIdField.setText(nextWaiterId());
        };
        identity.valueProperty().addListener((obs,oldChoice,newChoice)->{
            updateWaiterField.run();
            if(newChoice==null)return;
            if(!NEW_IDENTITY_ID.equals(newChoice.id())){
                String mapped=identityNames.getOrDefault(newChoice.id(),"");
                if(!mapped.isBlank()&&(name.getText().isBlank()||name.getText().trim().equals(lastAutoFilledName[0]))){
                    name.setText(mapped);
                    lastAutoFilledName[0]=mapped;
                }
            }
        });
        role.valueProperty().addListener((obs,prev,current)->{
            if(current==null)return;
            boolean waiter="waiter".equals(current);
            boolean assigned=waiter||"kitchen".equals(current)||"courier".equals(current);
            identity.getItems().clear();
            identityNames.clear();
            identity.setValue(null);
            identity.setDisable(!assigned);
            if(assigned){
                String roleLabel=waiter?"pelayan":("kitchen".equals(current)?"staf dapur":"kurir");
                Ui.Choice createNew=new Ui.Choice(NEW_IDENTITY_ID,"+ Buat identitas "+roleLabel+" baru");
                identity.getItems().add(createNew);
                populateAvailableIdentities(current,currentProfileId,identity,identityNames);
                if(row!=null){
                    String existingId=waiter?row.path("id_pelayan").asText(""):row.path("id_staff").asText("");
                    Ui.select(identity,existingId);
                }
                if(identity.getValue()==null)identity.setValue(createNew);
            }
            identity.setPromptText(assigned?"Pilih atau buat identitas":"Tidak memerlukan identitas staf");
            updateWaiterField.run();
        });
        role.setValue(row==null?"cashier":value(row,"role"));
        if(row!=null){
            name.setText(value(row,"display_name"));
            lastAutoFilledName[0]=name.getText();
        }
        CheckBox active=active(row);GridPane f=Ui.form();
        Ui.field(f,0,"UUID Auth user",uuid);
        Ui.field(f,1,"Peran",role);
        Ui.field(f,2,"Identitas staf",identity);
        Ui.field(f,3,"ID pelayan baru",waiterIdField);
        Ui.field(f,4,"Nama tampilan",name);
        Ui.field(f,5,"Aktif",active);
        editor("Profil pengguna",f,()->{
            String rawUuid=required(uuid);
            try{UUID.fromString(rawUuid);}catch(IllegalArgumentException ex){throw new IllegalArgumentException("Masukkan UUID pengguna Auth yang valid.");}
            if(rawUuid.equalsIgnoreCase(data.path("profile").path("id").asText()))
                throw new IllegalArgumentException("Perubahan profil sendiri dilakukan admin lain agar akses admin tidak terkunci.");
            String displayName=required(name);
            String selectedRole=role.getValue();
            ObjectNode p=Json.object().put("user_id",rawUuid).put("name",displayName).put("role",selectedRole).put("active",active.isSelected());
            if(!identity.isDisabled()){
                Ui.Choice selectedIdentity=identity.getValue();
                if(selectedIdentity==null)throw new IllegalArgumentException("Pilih identitas staf aktif atau buat identitas baru.");
                if(NEW_IDENTITY_ID.equals(selectedIdentity.id())){
                    splitPersonName(displayName);
                    p.put("create_identity",true);
                    if("waiter".equals(selectedRole))p.put("waiter_id",validateNewWaiterId(required(waiterIdField),createdWaiterId[0]));
                }else if("waiter".equals(selectedRole)){
                    p.put("waiter_id",selectedIdentity.id());
                }else{
                    p.put("staff_id",Integer.parseInt(selectedIdentity.id()));
                }
            }
            return p;
        },body->{
            ObjectNode requestBody=body.deepCopy();
            String selectedRole=requestBody.path("role").asText();
            boolean createNew=requestBody.path("create_identity").asBoolean(false);
            requestBody.remove("create_identity");
            if(createNew){
                String[] parts=splitPersonName(requestBody.path("name").asText());
                if("kitchen".equals(selectedRole)||"courier".equals(selectedRole)){
                    if(createdStaffId[0]==null||!selectedRole.equals(createdStaffRole[0])){
                        JsonNode saved=repository.execute("save_staff",Json.object().put("first",parts[0]).put("last",parts[1]).put("kind",selectedRole).put("active",true));
                        createdStaffId[0]=saved.path("id").asInt();
                        createdStaffRole[0]=selectedRole;
                    }else{
                        repository.execute("save_staff",Json.object().put("id",createdStaffId[0]).put("first",parts[0]).put("last",parts[1]).put("kind",selectedRole).put("active",true));
                    }
                    requestBody.put("staff_id",createdStaffId[0]);
                }else if("waiter".equals(selectedRole)){
                    String targetWaiterId=requestBody.path("waiter_id").asText();
                    boolean isNewRow=createdWaiterId[0]==null||!createdWaiterId[0].equals(targetWaiterId);
                    repository.execute("save_waiter",Json.object().put("waiter_id",targetWaiterId).put("first",parts[0]).put("last",parts[1]).put("active",true).put("is_new",isNewRow));
                    createdWaiterId[0]=targetWaiterId;
                    requestBody.put("waiter_id",targetWaiterId);
                }
            }
            return repository.execute("save_profile",requestBody);
        },()->{});
        if(createdStaffId[0]!=null||createdWaiterId[0]!=null)refresh();
    }
    private Node account(){
        Button change = Ui.button("Ganti kata sandi", this::changePassword);
        change.setId("account-change-password");
        VBox box = Ui.page(Ui.heading("Akun & Pengaturan"),Ui.label("Nama: "+data.path("profile").path("display_name").asText()),Ui.label("Peran: "+role()),Ui.label("UUID: "+data.path("profile").path("id").asText()),
            change, Ui.label("Versi 1.0.6 · JavaFX Desktop"));
        if (allowed("admin")) {
            // ── Pengaturan Restoran (hanya admin) ──────────────────────
            TextField restoName=new TextField(data.path("settings").path("display_name").asText());
            TextArea restoFooter=new TextArea(data.path("settings").path("receipt_footer").asText());restoFooter.setPrefRowCount(3);
            GridPane restoFields=Ui.form();Ui.field(restoFields,0,"Nama restoran",restoName);Ui.field(restoFields,1,"Footer nota",restoFooter);
            Button saveSettings=Ui.button("Simpan pengaturan",()->guard(()->mutation("settings",Json.object().put("name",required(restoName)).put("footer",restoFooter.getText()),r->{})));
            VBox settingsSection=new VBox(10,Ui.heading("Pengaturan Restoran"),restoFields,saveSettings);
            box.getChildren().addAll(new Separator(), settingsSection, new Separator(), userAccessSection());
            return Ui.scroll(box);
        }
        return box;
    }
    private void changePassword() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Ganti kata sandi");
        Ui.styleDialog(dialog);
        PasswordField current = new PasswordField();
        PasswordField password = new PasswordField();
        PasswordField confirmation = new PasswordField();
        GridPane fields = Ui.form();
        Ui.field(fields, 0, "Kata sandi lama", Ui.passwordBox(current));
        Ui.field(fields, 1, "Kata sandi baru", Ui.passwordBox(password));
        Ui.field(fields, 2, "Ulangi kata sandi baru", Ui.passwordBox(confirmation));
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(Ui.page(fields, Ui.label("Setelah menyimpan, masuk kembali dengan kata sandi baru.")));
        pane.getButtonTypes().addAll(Ui.SAVE, ButtonType.CANCEL);
        dialog.setOnCloseRequest(event -> { if (pane.isDisabled()) event.consume(); });
        pane.lookupButton(Ui.SAVE).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            guard(() -> {
                String oldSecret = current.getText();
                if (oldSecret.isEmpty()) throw new IllegalArgumentException("Masukkan kata sandi lama.");
                String newSecret = confirmedPassword(password, confirmation);
                if (oldSecret.equals(newSecret)) throw new IllegalArgumentException("Gunakan kata sandi yang berbeda dari kata sandi lama.");
                if (hasUnsavedOrder() && !Ui.confirm("Keranjang belum disimpan. Ganti kata sandi dan keluar?")) return;
                current.clear();
                password.clear();
                confirmation.clear();
                pane.setDisable(true);
                async.run(() -> { repository.changePassword(oldSecret, newSecret); return true; }, result -> {
                    pane.setDisable(false);
                    dialog.close();
                    accountNotice("Kata sandi berhasil diganti. Silakan masuk kembali dengan kata sandi baru.");
                    logout.run();
                }, error -> {
                    pane.setDisable(false);
                    Ui.error(error.getMessage());
                });
            });
        });
        dialog.showAndWait();
    }
    private static String confirmedPassword(PasswordField password, PasswordField confirmation) {
        String secret = password.getText();
        if (secret.isBlank() || secret.codePointCount(0, secret.length()) < 8) {
            throw new IllegalArgumentException("Masukkan kata sandi minimal 8 karakter.");
        }
        if (!secret.equals(confirmation.getText())) throw new IllegalArgumentException("Kedua kata sandi harus sama.");
        return secret;
    }
    private void accountNotice(String message) {
        Alert notice = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        notice.initOwner(stage);
        notice.setHeaderText(null);
        Ui.styleDialog(notice);
        notice.showAndWait();
    }
    private static String value(JsonNode n,String key){return n==null||n.path(key).isNull()?"":n.path(key).asText("");}
    private static CheckBox active(JsonNode row){CheckBox box=new CheckBox("Aktif");box.setSelected(row==null||row.path("is_active").asBoolean());return box;}
    private static String required(TextField field){String value=field.getText().trim();if(value.isBlank())throw new IllegalArgumentException("Kolom wajib belum diisi.");return value;}
    private void editor(String title,Node fields,String action,Supplier<ObjectNode> payload){
        editor(title,fields,()->{
            ObjectNode body=payload.get();
            return action.equals("pay")&&!Ui.confirm("Konfirmasi bahwa pembayaran sudah benar-benar diterima?")?null:body;
        },body->repository.execute(action,body),()->{});
    }
    private void editor(String title,Node fields,Supplier<ObjectNode> payload,Function<JsonNode,JsonNode> save,Runnable afterSave){
        Dialog<Void> dialog=new Dialog<>();Ui.styleDialog(dialog);dialog.initOwner(stage);dialog.setTitle(title);dialog.getDialogPane().setPrefWidth(650);
        dialog.getDialogPane().setContent(Ui.scroll(Ui.page(fields)));dialog.getDialogPane().getButtonTypes().addAll(Ui.SAVE,ButtonType.CANCEL);
        dialog.setOnCloseRequest(event->{if(dialog.getDialogPane().isDisabled())event.consume();});
        dialog.getDialogPane().lookupButton(Ui.SAVE).addEventFilter(javafx.event.ActionEvent.ACTION,event->{
            event.consume();guard(()->{
                ObjectNode body=payload.get();if(body==null)return;
                dialog.getDialogPane().setDisable(true);
                async.run(()->save.apply(body),result->{dialog.getDialogPane().setDisable(false);dialog.close();afterSave.run();refresh();},error->{dialog.getDialogPane().setDisable(false);Ui.error(error.getMessage());});
            });
        });dialog.showAndWait();
    }
    private void saveText(String suggested,String text){
        FileChooser chooser=new FileChooser();chooser.setTitle("Simpan hasil");chooser.setInitialFileName(suggested);var file=chooser.showSaveDialog(stage);if(file==null)return;
        try{Files.writeString(file.toPath(),text,StandardCharsets.UTF_8);}catch(Exception ex){Ui.error("Berkas tidak dapat disimpan. Periksa izin folder.");}
    }
}

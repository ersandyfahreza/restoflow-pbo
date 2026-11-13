package id.ac.restoflow.ui;

import com.fasterxml.jackson.databind.JsonNode;
import id.ac.restoflow.integration.Json;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

public final class Ui {
    public static final ButtonType SAVE=new ButtonType("Simpan",ButtonBar.ButtonData.OK_DONE);
    private Ui() {}
    public record Column(String title, Function<JsonNode,String> value) {}
    public record Choice(String id, String name) { @Override public String toString() { return name; } }
    public static final NumberFormat MONEY=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));
    public static java.time.LocalDate today(){return java.time.LocalDate.now(ZoneId.of("Asia/Jakarta"));}
    public static void dateFormat(DatePicker picker){
        var format=DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);
        picker.setConverter(new javafx.util.StringConverter<>(){
            public String toString(java.time.LocalDate date){return date==null?"":format.format(date);}
            public java.time.LocalDate fromString(String value){return value==null||value.isBlank()?null:java.time.LocalDate.parse(value,format);}
        });
    }
    public static void loadFonts(){
        for(String path:new String[]{"poppins/Poppins-Regular.ttf","rubik/Rubik[wght].ttf","ptserif/PT_Serif-Web-Regular.ttf"}){
            try(var stream=Ui.class.getResourceAsStream("/fonts/"+path)){if(stream!=null)javafx.scene.text.Font.loadFont(stream,14);}catch(java.io.IOException ignored){}
        }
    }
    static { MONEY.setMaximumFractionDigits(0); }
    public static String money(long value) { return MONEY.format(value); }
    public static String text(JsonNode n,String key) { return n.path(key).isNull()?"Belum diisi":n.path(key).asText("Belum diisi"); }
    public static String time(JsonNode n) {
        try { return DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm",Locale.forLanguageTag("id-ID"))
                .withZone(ZoneId.of("Asia/Jakarta")).format(Instant.parse(n.asText())); }
        catch(RuntimeException ex) { return "Waktu tidak tersedia"; }
    }
    public static Label label(String text) { Label l=new Label(text);l.setWrapText(true);return l; }
    public static Label heading(String text) { Label l=label(text);l.getStyleClass().add("page-title");return l; }
    public static Button button(String title,Runnable action) { Button b=new Button(title);b.setOnAction(e->action.run());return b; }
    public static void styleDialog(Dialog<?> dialog){dialog.getDialogPane().getStylesheets().add(Ui.class.getResource("/styles/app.css").toExternalForm());}
    public static void error(String message) { Alert a=new Alert(Alert.AlertType.ERROR,message,ButtonType.OK);a.setHeaderText("Tindakan belum berhasil");styleDialog(a);a.showAndWait(); }
    public static boolean confirm(String message) {
        Alert a=new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.OK,ButtonType.CANCEL);a.setHeaderText("Konfirmasi");
        styleDialog(a);
        return a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;
    }
    public static VBox page(Node... nodes) { VBox p=new VBox(12,nodes);p.setPadding(new Insets(18));return p; }
    public static ScrollPane scroll(Node node) { ScrollPane s=new ScrollPane(node);s.setFitToWidth(true);return s; }
    public static Column col(String title,String key) { return new Column(title,n->text(n,key)); }
    public static List<JsonNode> rows(JsonNode data,String key) {
        List<JsonNode> result=new ArrayList<>();data.path(key).forEach(result::add);return result;
    }
    public static String name(JsonNode data,String table,String idKey,String id,String nameKey) {
        return rows(data,table).stream().filter(n->n.path(idKey).asText().equals(id)).map(n->text(n,nameKey)).findFirst().orElse("#"+id);
    }
    public static id.ac.restoflow.domain.Staff staff(JsonNode n){
        int id=n.path("id_staff").asInt();String name=n.path("nama_staff").asText();boolean active=n.path("is_active").asBoolean();
        return "kitchen".equals(n.path("kind").asText())?new id.ac.restoflow.domain.KitchenStaff(id,name,active):new id.ac.restoflow.domain.Courier(id,name,active);
    }
    public static ComboBox<Choice> choices(JsonNode data,String table,String idKey,Function<JsonNode,String> caption,boolean activeOnly) {
        ComboBox<Choice> box=new ComboBox<>();box.setMaxWidth(Double.MAX_VALUE);box.setPromptText("Pilih...");
        for(JsonNode row:rows(data,table)) if(!activeOnly||row.path("is_active").asBoolean(true)) box.getItems().add(new Choice(row.path(idKey).asText(),caption.apply(row)));
        return box;
    }
    public static void select(ComboBox<Choice> box,String id) { box.getItems().stream().filter(c->c.id().equals(id)).findFirst().ifPresent(box::setValue); }
    public static Integer integerChoice(ComboBox<Choice> b) { return b.getValue()==null?null:Integer.valueOf(b.getValue().id()); }
    public static String stringChoice(ComboBox<Choice> b) { return b.getValue()==null?"":b.getValue().id(); }
    public static long amount(TextField field) {
        try { long n=Long.parseLong(field.getText().trim());if(n<0)throw new NumberFormatException();return n; }
        catch(NumberFormatException ex) { throw new IllegalArgumentException("Masukkan nominal rupiah bulat tanpa titik/koma dan tidak negatif."); }
    }
    public static GridPane form() {
        GridPane g=new GridPane();g.setHgap(12);g.setVgap(8);
        ColumnConstraints labels=new ColumnConstraints(120,135,180),inputs=new ColumnConstraints(140,320,Double.MAX_VALUE);
        inputs.setHgrow(Priority.ALWAYS);g.getColumnConstraints().addAll(labels,inputs);return g;
    }
    public static void field(GridPane form,int row,String title,Node input) {
        Label label=label(title);label.setLabelFor(input);form.add(label,0,row);form.add(input,1,row);
        if(input instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);GridPane.setHgrow(input,Priority.ALWAYS);
    }
    public static final class DataTable {
        public final TableView<JsonNode> table=new TableView<>();
        public final VBox node;
        public final TextField search=new TextField();
        private final List<JsonNode> source;
        private java.util.function.Predicate<JsonNode> rowFilter=row->true;
        public void setRowFilter(java.util.function.Predicate<JsonNode> predicate){rowFilter=predicate;filter();}
        private final Label count=label("");
        public DataTable(List<JsonNode> source,Column... cols) {
            this.source=source;
            for(Column c:cols) {
                TableColumn<JsonNode,String> column=new TableColumn<>(c.title());column.setMinWidth(80);column.setPrefWidth(160);
                column.setCellValueFactory(v->new ReadOnlyStringWrapper(c.value().apply(v.getValue())));table.getColumns().add(column);
            }
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            table.setPlaceholder(label(source.isEmpty()?"Belum ada data. Periksa rentang tanggal atau tambahkan data sesuai hak akses.":"Tidak ada hasil. Ubah atau kosongkan pencarian."));table.setPrefHeight(440);
            table.setAccessibleText("Daftar data. Pilih baris untuk menjalankan tindakan.");
            search.setPromptText("Cari nama, nomor, status, atau catatan...");
            search.getStyleClass().add("table-search");
            search.setAccessibleText("Cari pada daftar data");
            search.textProperty().addListener((obs,a,b)->filter());
            count.getStyleClass().add("table-count");
            node=new VBox(8,search,count,table);
            VBox.setVgrow(table,Priority.ALWAYS);filter();
        }
        private void filter() {
            String q=search.getText().toLowerCase(Locale.ROOT).trim();
            List<JsonNode> rows=source.stream().filter(rowFilter).filter(n->Json.write(n).toLowerCase(Locale.ROOT).contains(q)).toList();
            table.setItems(FXCollections.observableArrayList(rows));count.setText(rows.size()+" dari "+source.size()+" data yang dimuat");
        }
        public JsonNode selected() {
            JsonNode n=table.getSelectionModel().getSelectedItem();
            if(n==null)throw new IllegalArgumentException("Pilih satu baris terlebih dahulu.");return n;
        }
    }
}

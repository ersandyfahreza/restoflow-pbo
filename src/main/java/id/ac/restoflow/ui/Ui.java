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
    public static StackPane icon(String svgPathData) { return icon(svgPathData, 16); }
    public static StackPane icon(String svgPathData, double size) {
        javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
        path.setContent(svgPathData);
        path.getStyleClass().add("svg-icon-path");
        var bounds = path.getLayoutBounds();
        double maxDim = Math.max(bounds.getWidth(), bounds.getHeight());
        if (maxDim > 0) {
            double scale = size / maxDim;
            path.setScaleX(scale);
            path.setScaleY(scale);
        }
        StackPane box = new StackPane(new javafx.scene.Group(path));
        box.getStyleClass().add("svg-icon-box");
        box.setMinSize(size + 2, size + 2);
        box.setPrefSize(size + 2, size + 2);
        box.setMaxSize(size + 2, size + 2);
        return box;
    }
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
    public static void select(ComboBox<Choice> box,String id) {
        if(id==null||id.isBlank()){box.setValue(null);return;}
        box.getItems().stream().filter(c->c.id().equals(id)).findFirst().ifPresent(box::setValue);
    }
    public static Integer integerChoice(ComboBox<Choice> b) { return b.getValue()==null||b.getValue().id().isBlank()?null:Integer.valueOf(b.getValue().id()); }
    public static String stringChoice(ComboBox<Choice> b) { return b.getValue()==null||b.getValue().id().isBlank()?"":b.getValue().id(); }
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
    public static HBox passwordBox(PasswordField passwordField) {
        TextField visibleField = new TextField();
        visibleField.setPromptText(passwordField.getPromptText());
        javafx.beans.binding.Bindings.bindBidirectional(visibleField.textProperty(), passwordField.textProperty());
        visibleField.disableProperty().bind(passwordField.disableProperty());
        visibleField.setVisible(false);
        visibleField.setManaged(false);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        visibleField.setMaxWidth(Double.MAX_VALUE);
        StackPane stack = new StackPane(passwordField, visibleField);
        HBox.setHgrow(stack, Priority.ALWAYS);
        ToggleButton toggle = new ToggleButton("Lihat");
        toggle.getStyleClass().add("password-toggle");
        toggle.setFocusTraversable(false);
        toggle.setTooltip(new Tooltip("Tampilkan atau sembunyikan kata sandi"));
        toggle.selectedProperty().addListener((obs, oldVal, show) -> {
            toggle.setText(show ? "Tutup" : "Lihat");
            visibleField.setVisible(show);
            visibleField.setManaged(show);
            passwordField.setVisible(!show);
            passwordField.setManaged(!show);
        });
        HBox box = new HBox(6, stack, toggle);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
    public static <T> void enableMultiSelect(TableView<T> table, ToggleButton multiToggle) {
        enableMultiSelect(table, multiToggle, null);
    }
    public static <T> void enableMultiSelect(TableView<T> table, ToggleButton multiToggle, java.util.function.Consumer<T> doubleClickHandler) {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>();
            row.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, event -> {
                if (multiToggle != null && multiToggle.isSelected() && !row.isEmpty()
                        && event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                        && !event.isShiftDown() && !event.isControlDown() && !event.isMetaDown()) {
                    int index = row.getIndex();
                    var sm = table.getSelectionModel();
                    if (sm.isSelected(index)) sm.clearSelection(index);
                    else sm.select(index);
                    table.requestFocus();
                    event.consume();
                }
            });
            row.setOnMouseClicked(event -> {
                if (doubleClickHandler != null && !row.isEmpty()
                        && event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && (multiToggle == null || !multiToggle.isSelected())) {
                    doubleClickHandler.accept(row.getItem());
                }
            });
            return row;
        });
    }
    public static final class DataTable {
        public final TableView<JsonNode> table=new TableView<>();
        public final VBox node;
        public final TextField search=new TextField();
        public final Button clearSearch=button("×",search::clear);
        public final ToggleButton multiSelect=new ToggleButton("Pilih banyak");
        public final Button selectAll=button("Pilih semua",()->table.getSelectionModel().selectAll());
        public final Button clearSelection=button("Batal pilih",()->table.getSelectionModel().clearSelection());
        private final List<JsonNode> source;
        private java.util.function.Predicate<JsonNode> rowFilter=row->true;
        private java.util.function.Consumer<JsonNode> rowDoubleClick;
        public void setRowFilter(java.util.function.Predicate<JsonNode> predicate){rowFilter=predicate;filter();}
        public void onRowDoubleClick(java.util.function.Consumer<JsonNode> handler){this.rowDoubleClick=handler;}
        private final Label count=label("");
        private int filteredCount=0;
        public DataTable(List<JsonNode> source,Column... cols) {
            this.source=source;
            for(Column c:cols) {
                TableColumn<JsonNode,String> column=new TableColumn<>(c.title());column.setMinWidth(80);column.setPrefWidth(160);
                column.setCellValueFactory(v->new ReadOnlyStringWrapper(c.value().apply(v.getValue())));table.getColumns().add(column);
            }
            enableMultiSelect(table,multiSelect,item->{if(rowDoubleClick!=null)rowDoubleClick.accept(item);});
            multiSelect.getStyleClass().add("multi-select-toggle");
            multiSelect.setTooltip(new Tooltip("Aktifkan agar klik baris memilih atau membatalkan beberapa baris sekaligus tanpa menahan tombol Ctrl."));
            selectAll.setTooltip(new Tooltip("Pilih seluruh baris yang tampil pada daftar ini."));
            clearSelection.setTooltip(new Tooltip("Kosongkan seluruh pilihan baris."));
            clearSelection.setDisable(true);
            table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<JsonNode>)change->{
                updateCountLabel();
                clearSelection.setDisable(table.getSelectionModel().getSelectedItems().isEmpty());
            });
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            table.setPlaceholder(label(source.isEmpty()?"Belum ada data. Periksa rentang tanggal atau tambahkan data sesuai hak akses.":"Tidak ada hasil. Ubah atau kosongkan pencarian."));table.setPrefHeight(440);
            table.setAccessibleText("Daftar data. Pilih satu atau beberapa baris untuk menjalankan tindakan.");
            search.setPromptText("Cari...");
            search.getStyleClass().add("table-search");
            search.setAccessibleText("Cari pada daftar data");
            clearSearch.getStyleClass().add("search-clear-button");
            clearSearch.setTooltip(new Tooltip("Kosongkan pencarian (Esc)"));
            clearSearch.setVisible(false);
            clearSearch.setManaged(false);
            search.textProperty().addListener((obs,a,b)->{
                boolean hasText=b!=null&&!b.isEmpty();
                clearSearch.setVisible(hasText);
                clearSearch.setManaged(hasText);
                filter();
            });
            search.setOnKeyPressed(e->{
                if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE){
                    search.clear();
                    e.consume();
                }
            });
            HBox.setHgrow(search,Priority.ALWAYS);
            HBox searchBar=new HBox(6,search,clearSearch);
            searchBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            count.getStyleClass().add("table-count");
            Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
            HBox toolbar=new HBox(6,count,spacer,multiSelect,selectAll,clearSelection);
            toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            node=new VBox(8,searchBar,toolbar,table);
            VBox.setVgrow(table,Priority.ALWAYS);filter();
        }
        private void updateCountLabel() {
            int selected=table.getSelectionModel().getSelectedItems().size();
            String base=filteredCount+" dari "+source.size()+" data yang dimuat";
            count.setText(selected>0?base+" · "+selected+" dipilih":base);
        }
        private void filter() {
            String q=search.getText().toLowerCase(Locale.ROOT).trim();
            List<JsonNode> rows=source.stream().filter(rowFilter).filter(n->Json.write(n).toLowerCase(Locale.ROOT).contains(q)).toList();
            filteredCount=rows.size();
            table.setItems(FXCollections.observableArrayList(rows));
            selectAll.setDisable(rows.isEmpty());
            updateCountLabel();
        }
        public JsonNode selected() {
            JsonNode n=table.getSelectionModel().getSelectedItem();
            if(n==null)throw new IllegalArgumentException("Pilih minimal satu baris terlebih dahulu.");return n;
        }
        public List<JsonNode> selectedItems() {
            List<JsonNode> items=new ArrayList<>(table.getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).toList());
            if(items.isEmpty())throw new IllegalArgumentException("Pilih minimal satu baris terlebih dahulu.");
            return items;
        }
    }
}

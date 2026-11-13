package id.ac.restoflow.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import java.util.function.BooleanSupplier;

/** Bingkai desktop: kontrol jendela tetap aktif ketika konten sedang menunggu jaringan. */
public final class WindowFrame extends BorderPane {
    private static final double EDGE=6;
    private final Stage stage;
    private final Node content;
    private final BooleanProperty busy=new SimpleBooleanProperty();
    private final Label activity=Ui.label("");
    private BooleanSupplier canClose=()->true;
    private double pointerX,pointerY,startX,startY,startWidth,startHeight;
    private boolean north,south,east,west,resizing;

    public WindowFrame(Stage stage,Node content){
        this.stage=stage;this.content=content;
        if(stage.isShowing())throw new IllegalArgumentException("Pasang bingkai sebelum jendela ditampilkan.");
        stage.initStyle(StageStyle.UNDECORATED);stage.setResizable(true);
        getStyleClass().add("window-frame");setPadding(new Insets(1));setCenter(content);
        Label title=Ui.label("RestoFlow");title.getStyleClass().add("window-title");
        Label purpose=Ui.label("Manajemen restoran");purpose.getStyleClass().add("window-purpose");
        Region dragSpace=new Region();HBox.setHgrow(dragSpace,Priority.ALWAYS);
        Button minimize=control("window-minimize","\u2212","Minimalkan jendela",()->stage.setIconified(true));
        Button maximize=control("window-maximize","\u25a1","Maksimalkan jendela",this::toggleMaximize);
        Button close=control("window-close","\u00d7","Tutup aplikasi",this::requestClose);close.getStyleClass().add("window-close");
        stage.maximizedProperty().addListener((obs,old,value)->{
            setCursor(Cursor.DEFAULT);
            maximize.setText(value?"\u2750":"\u25a1");
            String label=value?"Pulihkan ukuran jendela":"Maksimalkan jendela";
            maximize.setAccessibleText(label);maximize.setTooltip(new Tooltip(label));
        });
        HBox caption=new HBox(12,title,purpose,dragSpace);caption.getStyleClass().add("window-caption");HBox.setHgrow(caption,Priority.ALWAYS);
        HBox header=new HBox(caption,minimize,maximize,close);header.getStyleClass().add("window-header");setTop(header);
        caption.setOnMousePressed(e->{if(e.getButton()==MouseButton.PRIMARY){pointerX=e.getScreenX();pointerY=e.getScreenY();startX=stage.getX();startY=stage.getY();}});
        caption.setOnMouseDragged(e->{
            if(!e.isPrimaryButtonDown()||resizing)return;
            if(stage.isMaximized()){
                double fraction=Math.max(.1,Math.min(.9,e.getSceneX()/getWidth()));stage.setMaximized(false);
                stage.setX(e.getScreenX()-stage.getWidth()*fraction);stage.setY(e.getScreenY()-18);
                pointerX=e.getScreenX();pointerY=e.getScreenY();startX=stage.getX();startY=stage.getY();
            }else{stage.setX(startX+e.getScreenX()-pointerX);stage.setY(startY+e.getScreenY()-pointerY);}
        });
        caption.setOnMouseClicked(e->{if(e.getButton()==MouseButton.PRIMARY&&e.getClickCount()==2)toggleMaximize();});
        activity.getStyleClass().add("activity-status");activity.setMaxWidth(Double.MAX_VALUE);
        activity.setVisible(false);activity.setManaged(false);setBottom(activity);
        busy.addListener((obs,old,value)->{
            content.setDisable(value);activity.setVisible(value);activity.setManaged(value);
            activity.setText(value?"Memuat data dari server. Tunggu hingga proses selesai.":"");
        });
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST,e->{if(!canClose.getAsBoolean())e.consume();});
        stage.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST,e->{if(!e.isConsumed())stage.hide();});
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{
            if(e.isAltDown()&&e.getCode()==KeyCode.F4){requestClose();e.consume();}
            else if(e.isShortcutDown()&&e.getCode()==KeyCode.F11){toggleMaximize();e.consume();}
        });
        addEventFilter(MouseEvent.MOUSE_MOVED,this::resizeCursor);
        addEventFilter(MouseEvent.MOUSE_PRESSED,this::beginResize);
        addEventFilter(MouseEvent.MOUSE_DRAGGED,this::resize);
        addEventFilter(MouseEvent.MOUSE_RELEASED,e->{resizing=false;});
    }
    private Button control(String id,String glyph,String label,Runnable action){
        Button button=Ui.button(glyph,action);button.setId(id);button.setAccessibleText(label);button.setTooltip(new Tooltip(label));
        button.getStyleClass().add("window-control");button.setMinSize(46,38);button.setPrefSize(46,38);button.setMaxSize(46,38);return button;
    }
    public void setCanClose(BooleanSupplier canClose){this.canClose=canClose;}
    public void setBusy(boolean value){busy.set(value);}
    public boolean isBusy(){return busy.get();}
    public void requestClose(){Event.fireEvent(stage,new WindowEvent(stage,WindowEvent.WINDOW_CLOSE_REQUEST));}
    public void toggleMaximize(){stage.setMaximized(!stage.isMaximized());}

    private void resizeCursor(MouseEvent e){
        if(stage.isMaximized()||stage.isFullScreen())return;
        double x=e.getSceneX(),y=e.getSceneY();
        boolean n=y<EDGE,s=y>getHeight()-EDGE,w=x<EDGE,r=x>getWidth()-EDGE;
        setCursor(n&&w||s&&r?Cursor.NW_RESIZE:n&&r||s&&w?Cursor.NE_RESIZE:n||s?Cursor.N_RESIZE:w||r?Cursor.E_RESIZE:Cursor.DEFAULT);
    }
    private void beginResize(MouseEvent e){
        if(e.getButton()!=MouseButton.PRIMARY||stage.isMaximized()||stage.isFullScreen())return;
        north=e.getSceneY()<EDGE;south=e.getSceneY()>getHeight()-EDGE;
        west=e.getSceneX()<EDGE;east=e.getSceneX()>getWidth()-EDGE;resizing=north||south||west||east;
        if(resizing){pointerX=e.getScreenX();pointerY=e.getScreenY();startX=stage.getX();startY=stage.getY();startWidth=stage.getWidth();startHeight=stage.getHeight();e.consume();}
    }
    private void resize(MouseEvent e){
        if(!resizing||!e.isPrimaryButtonDown())return;
        double dx=e.getScreenX()-pointerX,dy=e.getScreenY()-pointerY;
        if(east||west){double width=Math.max(stage.getMinWidth(),startWidth+(west?-dx:dx));stage.setWidth(width);if(west)stage.setX(startX+startWidth-width);}
        if(north||south){double height=Math.max(stage.getMinHeight(),startHeight+(north?-dy:dy));stage.setHeight(height);if(north)stage.setY(startY+startHeight-height);}
        e.consume();
    }
    public static void fitToScreen(Stage stage){
        Rectangle2D area=Screen.getPrimary().getVisualBounds();stage.setMinWidth(1000);stage.setMinHeight(640);
        stage.setWidth(Math.min(1360,Math.max(1000,area.getWidth()-64)));
        stage.setHeight(Math.min(860,Math.max(640,area.getHeight()-48)));
        stage.centerOnScreen();
    }
}

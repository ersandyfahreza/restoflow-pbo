package id.ac.restoflow.ui;

import id.ac.restoflow.integration.Json;
import id.ac.restoflow.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {
    @Test void allOperationalScreensRenderUsingTestOnlyFixture() throws Exception {
        CountDownLatch started=new CountDownLatch(1);Platform.startup(()->{Platform.setImplicitExit(false);started.countDown();});
        assertTrue(started.await(15,TimeUnit.SECONDS));
        FutureTask<Void> task=new FutureTask<>(()->{
            JsonNode fixture=Json.read(Files.readString(Path.of("src/test/resources/workspace-fixture.json")));
            Ui.DataTable allRows=new Ui.DataTable(java.util.Collections.nCopies(25,Ui.rows(fixture,"menu").get(0)),Ui.col("Menu","nama_menu"));
            assertEquals(25,allRows.table.getItems().size());
            assertNull(allRows.node.lookup(".pagination"));
            RestaurantRepository repository=new RestaurantRepository(){
                public JsonNode snapshot(java.time.LocalDate from,java.time.LocalDate to){return fixture;}
                public JsonNode execute(String action,JsonNode data){throw new AssertionError("Rendering must not write business data");}
            };
            Workspace.Async async=new Workspace.Async(){public <T>void run(Callable<T> work,Consumer<T> success,Consumer<Throwable> failure){try{success.accept(work.call());}catch(Exception ex){failure.accept(ex);}}};
            Ui.loadFonts();Stage stage=new Stage();Workspace workspace=new Workspace(stage,repository,async,()->{},fixture);
            WindowFrame window=new WindowFrame(stage,workspace.node());
            Scene scene=new Scene(window,1366,900);scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());stage.setScene(scene);stage.show();
            try{
                for(String page:new String[]{"Dashboard","POS","Pesanan","Dapur","Menu","Meja","Pembayaran","Nota","Staf","Pengiriman","Ulasan","Laporan","Pengaturan","Akun"}){
                    Button navigation=(Button)scene.getRoot().lookupAll(".nav-button").stream().filter(n->((Button)n).getText().equals(page)).findFirst().orElseThrow();
                    navigation.fire();scene.getRoot().applyCss();scene.getRoot().layout();assertNotNull(workspace.node().getCenter(),page);
                    assertTrue(scene.getRoot().lookupAll(".pagination").isEmpty(),page);
                    assertNull(workspace.node().getBottom(),page);
                    assertNull(workspace.node().getTop(),page);
                    assertNotNull(scene.getRoot().lookup(".sidebar-action"),page);
                    assertNull(scene.getRoot().lookup("#zoom-in"),page);
                    if(page.equals("Dapur")){
                        Button processing=(Button)scene.lookup("#order-status-Diproses"),ready=(Button)scene.lookup("#order-status-Siap"),done=(Button)scene.lookup("#order-status-Selesai");
                        assertTrue(processing.isDisabled());
                        TableView<?> orders=(TableView<?>)scene.lookup(".table-view");orders.getSelectionModel().selectFirst();
                        assertTrue(processing.isDisabled());assertTrue(ready.isDisabled());assertTrue(done.isDisabled());
                        assertTrue(done.getTooltip().getText().contains("Terkirim"));
                    }
                    if(page.equals("Pesanan")){
                        assertTrue(((Button)scene.lookup("#order-pay")).isDisabled());
                        ((TableView<?>)scene.lookup(".table-view")).getSelectionModel().selectFirst();
                        assertTrue(((Button)scene.lookup("#order-pay")).isDisabled());
                        assertTrue(((Button)scene.lookup("#order-edit")).isDisabled());
                    }
                    if(page.equals("POS")){
                        var tables=scene.getRoot().lookupAll(".table-view");assertEquals(2,tables.size());
                        for(Node table:tables)assertSame(TableView.CONSTRAINED_RESIZE_POLICY,((TableView<?>)table).getColumnResizePolicy());
                        long activeMenus=Ui.rows(fixture,"menu").stream().filter(m->m.path("is_active").asBoolean()).count();
                        assertTrue(tables.stream().map(TableView.class::cast).anyMatch(table->table.getItems().size()==activeMenus));
                    }
                    if(java.util.Set.of("Dashboard","POS","Pengiriman").contains(page)){
                        var snapshot=scene.getRoot().snapshot(null,null);BufferedImage image=new BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),BufferedImage.TYPE_INT_ARGB);
                        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
                        Files.createDirectories(Path.of("target/ui"));ImageIO.write(image,"png",Path.of("target/ui/"+page+".png").toFile());
                    }
                }
                Node title=workspace.node().getCenter().lookup(".page-title");
                double originalFontSize=((Labeled)title).getFont().getSize();
                pressZoom(workspace,KeyCode.PLUS);scene.getRoot().applyCss();
                assertTrue(workspace.node().getStyle().contains("14.3px"));
                assertTrue(((Labeled)title).getFont().getSize()>originalFontSize);
                pressZoom(workspace,KeyCode.MINUS);assertTrue(workspace.node().getStyle().contains("13.0px"));
                for(int i=0;i<9;i++)pressZoom(workspace,KeyCode.MINUS);
                assertTrue(workspace.node().getStyle().contains("10.4px"));
                for(int i=0;i<6;i++)pressZoom(workspace,KeyCode.PLUS);
                assertTrue(workspace.node().getStyle().contains("18.2px"));
                pressZoom(workspace,KeyCode.DIGIT0);assertTrue(workspace.node().getStyle().contains("13.0px"));
                var paymentFixture=fixture.deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode)paymentFixture.path("orders").get(0)).put("status","Diproses").put("payment_status","Pending");
                Workspace paymentWorkspace=new Workspace(stage,repository,async,()->{},paymentFixture);
                window.setCenter(paymentWorkspace.node());
                for(String paymentPage:new String[]{"Pesanan","Pembayaran"}){
                    paymentWorkspace.show(paymentPage);window.applyCss();window.layout();
                    ((TableView<?>)window.lookup(".table-view")).getSelectionModel().selectFirst();
                    Button pay=(Button)window.lookup(paymentPage.equals("Pesanan")?"#order-pay":"#payment-pay");assertFalse(pay.isDisabled());
                    if(paymentPage.equals("Pesanan"))assertTrue(((Button)window.lookup("#order-edit")).isDisabled());
                    FutureTask<Void> dialogCheck=new FutureTask<>(()->{
                        var dialogWindow=javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().getRoot() instanceof javafx.scene.control.DialogPane).findFirst().orElseThrow();
                        var pane=(javafx.scene.control.DialogPane)dialogWindow.getScene().getRoot();
                        try{
                            @SuppressWarnings("unchecked") var method=(javafx.scene.control.ComboBox<String>)pane.lookup("#payment-method");
                            TextField received=(TextField)pane.lookup("#payment-received");assertEquals("56000",received.getText());
                            method.setValue("QRIS");assertTrue(received.isDisabled());method.setValue("Cash");assertFalse(received.isDisabled());
                        }finally{((Button)pane.lookupButton(javafx.scene.control.ButtonType.CANCEL)).fire();}return null;
                    });Platform.runLater(dialogCheck);pay.fire();dialogCheck.get(5,TimeUnit.SECONDS);
                }
                window.setCenter(workspace.node());workspace.show("POS");
                for(int width:new int[]{1024,1366}){
                    stage.setWidth(width);stage.setHeight(width==1024?700:900);
                    for(int zoom:new int[]{80,100,140}){
                        pressZoom(workspace,KeyCode.DIGIT0);
                        for(int i=100;i<zoom;i+=10)pressZoom(workspace,KeyCode.PLUS);
                        for(int i=100;i>zoom;i-=10)pressZoom(workspace,KeyCode.MINUS);
                        // Stage resize is asynchronous on Windows; explicitly size the layout root here.
                        window.resize(width,width==1024?700:900);
                        scene.getRoot().applyCss();scene.getRoot().layout();
                        assertEquals(width,window.getWidth());
                        Button save=(Button)scene.lookup("#pos-save");assertTrue(save.isDisabled());
                        var bounds=save.localToScene(save.getBoundsInLocal());
                        assertTrue(bounds.getMaxX()<=width+1,"Save outside width "+width+" zoom "+zoom);
                        assertTrue(bounds.getMaxY()<=(width==1024?700:900)+1,"Save outside height "+width+" zoom "+zoom);
                        screenshot(scene,"POS-"+width+"-"+zoom);
                    }
                }
                pressZoom(workspace,KeyCode.DIGIT0);
                var tables=scene.getRoot().lookupAll(".table-view");
                @SuppressWarnings("unchecked") TableView<JsonNode> menu=(TableView<JsonNode>)tables.stream().filter(n->!"pos-cart".equals(n.getId())).findFirst().orElseThrow();
                @SuppressWarnings("unchecked") TableView<id.ac.restoflow.domain.Cart.Item> cart=(TableView<id.ac.restoflow.domain.Cart.Item>)scene.lookup("#pos-cart");
                Button add=(Button)scene.lookup("#pos-add");assertTrue(add.isDisabled());menu.getSelectionModel().selectFirst();assertFalse(add.isDisabled());add.fire();
                assertEquals(1,cart.getItems().size());assertTrue(workspace.hasUnsavedOrder());
                assertFalse(((Button)scene.lookup("#pos-save")).isDisabled());
                cart.getSelectionModel().selectFirst();((Button)scene.lookup("#pos-increase")).fire();assertEquals(2,cart.getItems().get(0).quantity());
                cart.getSelectionModel().selectFirst();((Button)scene.lookup("#pos-decrease")).fire();assertEquals(1,cart.getItems().get(0).quantity());
                cart.getSelectionModel().selectFirst();((Button)scene.lookup("#pos-remove")).fire();assertTrue(cart.getItems().isEmpty());assertFalse(workspace.hasUnsavedOrder());
                ((Button)scene.lookup("#pos-new")).fire();assertTrue(cart.getItems().isEmpty());
                menu.getSelectionModel().selectFirst();
                ((TextField)menu.getParent().lookup(".table-search")).setText("___tidak_ada___");assertTrue(menu.getItems().isEmpty());assertTrue(add.isDisabled());
                int[] saved={0};PosView pos=new PosView(fixture,p->saved[0]++);window.setCenter(pos.node());window.applyCss();window.layout();
                @SuppressWarnings("unchecked") javafx.scene.control.ComboBox<String> category=(javafx.scene.control.ComboBox<String>)window.lookup("#pos-category");
                category.setValue("Makanan");
                @SuppressWarnings("unchecked") TableView<JsonNode> catalog=(TableView<JsonNode>)window.lookupAll(".table-view").stream().filter(n->!"pos-cart".equals(n.getId())).findFirst().orElseThrow();
                assertEquals(1,catalog.getItems().size());category.setValue("Semua kategori");assertEquals(2,catalog.getItems().size());
                TextField catalogSearch=(TextField)window.lookup(".table-search");catalogSearch.setText("Nasi");category.setValue("Minuman");assertEquals(0,catalog.getItems().size());
                category.setValue("Makanan");assertEquals(1,catalog.getItems().size());catalogSearch.clear();category.setValue("Semua kategori");
                @SuppressWarnings("unchecked") javafx.scene.control.Spinner<Integer> quantity=(javafx.scene.control.Spinner<Integer>)window.lookup("#pos-quantity");quantity.increment();
                catalog.getSelectionModel().selectFirst();((Button)window.lookup("#pos-add")).fire();assertEquals(2,pos.cart.items().iterator().next().quantity());
                pos.type.setValue("delivery");assertFalse(pos.address.isDisabled());assertTrue(pos.table.isDisabled());
                pos.address.setText("Alamat khusus tes");pos.notes.setText("Catatan khusus tes");
                ((Button)window.lookup("#pos-save")).fire();assertEquals(1,saved[0]);
                answerConfirmation(()->((Button)window.lookup("#pos-new")).fire(),javafx.scene.control.ButtonType.CANCEL);assertFalse(pos.cart.isEmpty());
                answerConfirmation(()->((Button)window.lookup("#pos-new")).fire(),javafx.scene.control.ButtonType.OK);assertTrue(pos.cart.isEmpty());assertEquals("",pos.address.getText());assertEquals("dine_in",pos.type.getValue());
                javafx.scene.control.TitledPane details=(javafx.scene.control.TitledPane)window.lookup("#pos-details");details.setExpanded(false);assertFalse(details.isExpanded());details.setExpanded(true);assertTrue(details.isExpanded());
                return null;
            }finally{stage.close();}
        });
        try{Platform.runLater(task);task.get(60,TimeUnit.SECONDS);windowLifecycle();}finally{Platform.exit();}
    }
    private void windowLifecycle() throws Exception {
        Stage stage=fx(()->{
            Stage s=new Stage();javafx.scene.layout.BorderPane content=new javafx.scene.layout.BorderPane();
            int[] calls={0,0};
            LoginView login=new LoginView(false,(email,password)->{assertEquals("demo@example.test",email);assertEquals("temporary",password);calls[0]++;},()->calls[1]++);
            content.setCenter(login);WindowFrame frame=new WindowFrame(s,content);WindowFrame.fitToScreen(s);
            Scene scene=new Scene(frame,1024,700);scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());s.setScene(scene);s.show();frame.applyCss();frame.layout();
            Button submit=(Button)scene.lookup("#login-submit");submit.fire();assertTrue(scene.lookup("#login-validation").isVisible());
            TextField email=(TextField)scene.lookup("#login-email");PasswordField password=(PasswordField)scene.lookup("#login-password");
            email.setText(" demo@example.test ");password.setText("temporary");submit.fire();assertEquals(1,calls[0]);assertEquals("",password.getText());
            ((Button)scene.lookup("#login-connection")).fire();assertEquals(1,calls[1]);
            screenshot(scene,"Login");
            frame.setBusy(true);assertTrue(content.isDisabled());assertFalse(scene.lookup("#window-minimize").isDisabled());assertTrue(scene.lookup(".activity-status").isVisible());
            frame.setCanClose(()->false);((Button)scene.lookup("#window-close")).fire();assertTrue(s.isShowing());
            frame.setBusy(false);assertFalse(content.isDisabled());assertFalse(scene.lookup(".activity-status").isVisible());
            ((Button)scene.lookup("#window-maximize")).fire();return s;
        });
        try{
            awaitState(()->stage.isMaximized());
            fx(()->{assertEquals("Pulihkan ukuran jendela",stage.getScene().lookup("#window-maximize").getAccessibleText());((Button)stage.getScene().lookup("#window-maximize")).fire();return null;});
            awaitState(()->!stage.isMaximized());
            fx(()->{((Button)stage.getScene().lookup("#window-minimize")).fire();return null;});awaitState(()->stage.isIconified());
            fx(()->{stage.setIconified(false);return null;});awaitState(()->!stage.isIconified());
            fx(()->{
                WindowFrame frame=(WindowFrame)stage.getScene().getRoot();Node caption=frame.lookup(".window-caption");double x=stage.getX(),y=stage.getY();
                mouse(caption,javafx.scene.input.MouseEvent.MOUSE_PRESSED,30,20,200,200,1,true);
                mouse(caption,javafx.scene.input.MouseEvent.MOUSE_DRAGGED,30,20,220,215,1,true);
                assertEquals(x+20,stage.getX(),1);assertEquals(y+15,stage.getY(),1);
                double width=stage.getWidth();
                mouse(frame,javafx.scene.input.MouseEvent.MOUSE_PRESSED,frame.getWidth()-2,100,500,300,1,true);
                mouse(frame,javafx.scene.input.MouseEvent.MOUSE_DRAGGED,frame.getWidth()-2,100,540,300,1,true);
                mouse(frame,javafx.scene.input.MouseEvent.MOUSE_RELEASED,frame.getWidth()-2,100,540,300,1,false);
                assertEquals(width+40,stage.getWidth(),1);
                mouse(caption,javafx.scene.input.MouseEvent.MOUSE_CLICKED,30,20,220,215,2,false);return null;
            });awaitState(()->stage.isMaximized());
            fx(()->{javafx.event.Event.fireEvent(stage.getScene().getRoot(),new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.F11,false,true,false,false));return null;});awaitState(()->!stage.isMaximized());
            fx(()->{javafx.event.Event.fireEvent(stage.getScene().getRoot(),new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.F4,false,false,true,false));assertTrue(stage.isShowing());return null;});
            fx(()->{WindowFrame frame=(WindowFrame)stage.getScene().getRoot();frame.setCanClose(()->true);((Button)stage.getScene().lookup("#window-close")).fire();assertFalse(stage.isShowing());return null;});
        }finally{fx(()->{stage.hide();return null;});}
    }
    private static void mouse(Node target,javafx.event.EventType<javafx.scene.input.MouseEvent> type,double x,double y,double screenX,double screenY,int clicks,boolean down){
        javafx.event.Event.fireEvent(target,new javafx.scene.input.MouseEvent(type,x,y,screenX,screenY,javafx.scene.input.MouseButton.PRIMARY,clicks,false,false,false,false,down,false,false,false,false,true,new javafx.scene.input.PickResult(target,x,y)));
    }
    private static void answerConfirmation(Runnable action,javafx.scene.control.ButtonType answer){
        Platform.runLater(()->{
            javafx.stage.Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene().getRoot() instanceof javafx.scene.control.DialogPane).findFirst().ifPresent(w->{
                var pane=(javafx.scene.control.DialogPane)w.getScene().getRoot();((Button)pane.lookupButton(answer)).fire();
            });
        });action.run();
    }
    private static <T>T fx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);}
    private static void awaitState(Callable<Boolean> state)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end){if(fx(state))return;Thread.sleep(50);}fail("Window manager did not reach expected state");
    }
    private static void screenshot(Scene scene,String name)throws Exception{
        scene.getRoot().applyCss();scene.getRoot().layout();
        var snapshot=scene.getRoot().snapshot(null,null);BufferedImage image=new BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
        Files.createDirectories(Path.of("target/ui"));ImageIO.write(image,"png",Path.of("target/ui/"+name+".png").toFile());
    }
    private static void pressZoom(Workspace workspace,KeyCode code){
        javafx.event.Event.fireEvent(workspace.node(),new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,false,true,false,false));
    }
}

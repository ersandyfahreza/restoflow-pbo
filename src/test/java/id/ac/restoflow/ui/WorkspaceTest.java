package id.ac.restoflow.ui;

import id.ac.restoflow.integration.Json;
import id.ac.restoflow.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableView;
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
            Scene scene=new Scene(workspace.node(),1366,900);scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());stage.setScene(scene);stage.show();
            try{
                for(String page:new String[]{"Dashboard","POS","Pesanan","Dapur","Menu","Meja","Pembayaran","Nota","Staf","Pengiriman","Ulasan","Laporan","Pengaturan","Akun"}){
                    workspace.show(page);scene.getRoot().applyCss();scene.getRoot().layout();assertNotNull(workspace.node().getCenter(),page);
                    assertTrue(scene.getRoot().lookupAll(".pagination").isEmpty(),page);
                    assertNull(workspace.node().getBottom(),page);
                    assertNull(workspace.node().getTop(),page);
                    assertNotNull(scene.getRoot().lookup(".sidebar-action"),page);
                    assertNull(scene.getRoot().lookup("#zoom-in"),page);
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
                return null;
            }finally{stage.close();}
        });
        Platform.runLater(task);task.get(60,TimeUnit.SECONDS);Platform.exit();
    }
    private static void pressZoom(Workspace workspace,KeyCode code){
        javafx.event.Event.fireEvent(workspace.node(),new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,false,true,false,false));
    }
}

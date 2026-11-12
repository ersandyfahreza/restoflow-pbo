package id.ac.restoflow.ui;

import id.ac.restoflow.integration.Json;
import id.ac.restoflow.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.Scene;
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
                    if(java.util.Set.of("Dashboard","POS","Pengiriman").contains(page)){
                        var snapshot=scene.getRoot().snapshot(null,null);BufferedImage image=new BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),BufferedImage.TYPE_INT_ARGB);
                        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,snapshot.getPixelReader().getArgb(x,y));
                        Files.createDirectories(Path.of("target/ui"));ImageIO.write(image,"png",Path.of("target/ui/"+page+".png").toFile());
                    }
                }
                return null;
            }finally{stage.close();}
        });
        Platform.runLater(task);task.get(60,TimeUnit.SECONDS);Platform.exit();
    }
}

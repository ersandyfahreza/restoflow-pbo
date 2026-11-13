package id.ac.restoflow;

import com.fasterxml.jackson.databind.JsonNode;
import id.ac.restoflow.config.AppConfig;
import id.ac.restoflow.integration.*;
import id.ac.restoflow.repository.*;
import id.ac.restoflow.ui.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class RestoFlowApp extends Application {
    private final BorderPane root=new BorderPane();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"restoflow-network");t.setDaemon(true);return t;});
    private Stage stage;
    private SupabaseClient client;
    private AppConfig config;
    private Workspace workspace;
    private WindowFrame frame;

    @Override public void start(Stage stage) {
        this.stage=stage;
        Ui.loadFonts();
        try {config=AppConfig.load(AppConfig.defaultPath(),System.getenv());}
        catch(Exception ex){config=new AppConfig("","");}
        frame=new WindowFrame(stage,root);
        frame.setCanClose(()->{
            if(frame.isBusy())return Ui.confirm("Masih ada permintaan yang berjalan. Tutup aplikasi? Hasil penyimpanan perlu diperiksa saat masuk kembali.");
            return workspace==null||!workspace.hasUnsavedOrder()||Ui.confirm("Keranjang belum disimpan. Tetap tutup aplikasi?");
        });
        Scene scene=new Scene(frame,1280,820);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setScene(scene);stage.setTitle("RestoFlow - Manajemen Restoran");WindowFrame.fitToScreen(stage);
        stage.setOnCloseRequest(e->worker.shutdownNow());
        showLogin();stage.show();
        if(getParameters().getRaw().contains("--smoke-test")) {
            root.applyCss();root.layout();
            if(scene.lookup("#login-title")==null)throw new IllegalStateException("Layar login tidak terbentuk.");
            System.out.println("UI_SMOKE_OK");
            var close=new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));close.setOnFinished(e->Platform.exit());close.play();
        }
    }
    private void showLogin() {
        workspace=null;root.setTop(null);root.setLeft(null);root.setBottom(null);
        root.setCenter(new LoginView(config.isConfigured(),(emailValue,secret)->{
            if(!config.isConfigured()){Ui.error("Atur koneksi Supabase terlebih dahulu.");return;}
            SupabaseClient candidate=new SupabaseClient(config);
            background(()->{
                try {candidate.login(emailValue,secret);return new SupabaseRepository(candidate).snapshot(Ui.today().minusDays(90),Ui.today());}
                catch(RuntimeException ex){candidate.clearSession();throw ex;}
            },data->{
                client=candidate;
                workspace=new Workspace(stage,new SupabaseRepository(client),this::background,this::logout,data);
                root.setCenter(workspace.node());
            });
        },this::configure));
    }
    private void configure() {
        Dialog<ButtonType> dialog=new Dialog<>();Ui.styleDialog(dialog);dialog.initOwner(stage);dialog.setTitle("Koneksi Supabase");
        TextField url=new TextField(config.url());PasswordField key=new PasswordField();key.setText(config.publishableKey());
        GridPane form=Ui.form();Ui.field(form,0,"Project URL (HTTPS)",url);Ui.field(form,1,"Publishable / anon key",key);
        VBox body=Ui.page(Ui.label("Gunakan publishable key. Secret key, service_role, dan password database tidak boleh dipakai pada aplikasi desktop."),form,
            Ui.label("Pengaturan disimpan pada folder .restoflow pengguna. Environment SUPABASE_URL/SUPABASE_PUBLISHABLE_KEY, bila ada, mengesampingkan nilai tersimpan saat startup."));
        dialog.getDialogPane().setContent(body);dialog.getDialogPane().setPrefWidth(640);
        dialog.getDialogPane().getButtonTypes().addAll(Ui.SAVE,ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(Ui.SAVE).addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            try {AppConfig updated=new AppConfig(url.getText().trim(),key.getText().trim());updated.save(AppConfig.defaultPath());config=updated;}
            catch(Exception ex){e.consume();Ui.error("Konfigurasi belum tersimpan: "+ex.getMessage());}
        });
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)==Ui.SAVE)showLogin();
    }
    private void logout() {
        background(()->{try{client.logout();}catch(RuntimeException ignored){}return true;},ignored->{client=null;showLogin();});
    }
    private <T> void background(Callable<T> call,Consumer<T> success,Consumer<Throwable> failure) {
        frame.setBusy(true);
        Task<T> task=new Task<>(){@Override protected T call() throws Exception{return call.call();}};
        task.setOnSucceeded(e->{frame.setBusy(false);try{success.accept(task.getValue());}catch(RuntimeException ex){Ui.error(ex.getMessage());}});
        task.setOnFailed(e->{frame.setBusy(false);failure.accept(task.getException());});
        worker.execute(task);
    }
    private <T> void background(Callable<T> call,Consumer<T> success) {background(call,success,error->Ui.error(error.getMessage()));}
}

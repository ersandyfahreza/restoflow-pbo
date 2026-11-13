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

    @Override public void start(Stage stage) {
        this.stage=stage;
        Ui.loadFonts();
        try {config=AppConfig.load(AppConfig.defaultPath(),System.getenv());}
        catch(Exception ex){config=new AppConfig("","");}
        Scene scene=new Scene(root,1280,820);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setScene(scene);stage.setTitle("RestoFlow — Manajemen Restoran");stage.setMinWidth(1000);stage.setMinHeight(700);
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
        Label title=Ui.heading("Selamat datang di RestoFlow");title.setId("login-title");
        TextField email=new TextField();email.setPromptText("Email akun Supabase Auth");
        PasswordField password=new PasswordField();password.setPromptText("Password");
        Button login=Ui.button("Masuk",()->{
            if(!config.isConfigured()){Ui.error("Atur koneksi Supabase terlebih dahulu.");return;}
            if(email.getText().isBlank()||password.getText().isBlank()){Ui.error("Email dan password wajib diisi.");return;}
            String secret=password.getText(),emailValue=email.getText();password.clear();
            SupabaseClient candidate=new SupabaseClient(config);
            background(()->{
                try {candidate.login(emailValue,secret);return new SupabaseRepository(candidate).snapshot(Ui.today().minusDays(90),Ui.today());}
                catch(RuntimeException ex){candidate.clearSession();throw ex;}
            },data->{
                client=candidate;
                workspace=new Workspace(stage,new SupabaseRepository(client),this::background,this::logout,data);
                root.setCenter(workspace.node());
            });
        });
        login.getStyleClass().add("primary");login.setDefaultButton(true);
        GridPane fields=Ui.form();Ui.field(fields,0,"Email",email);Ui.field(fields,1,"Password",password);
        VBox card=Ui.page(title,Ui.label("Kasir, dapur, dan pengiriman dalam satu aplikasi desktop."),fields,login,
            Ui.button("Atur koneksi database",this::configure),
            Ui.label(config.isConfigured()?"Konfigurasi tersimpan. Masuk untuk memeriksa akses akun.":"Belum terhubung. Isi URL dan publishable key melalui pengaturan koneksi."),
            Ui.label("Akun dibuat oleh admin melalui Supabase Auth, lalu diberi profil/peran aplikasi. Password dan sesi tidak disimpan ke disk."));
        card.setMaxWidth(650);card.getStyleClass().add("card");
        StackPane container=new StackPane(card);container.setPadding(new javafx.geometry.Insets(50));root.setCenter(container);
    }
    private void configure() {
        Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(stage);dialog.setTitle("Koneksi Supabase");
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
        root.setDisable(true);
        Task<T> task=new Task<>(){@Override protected T call() throws Exception{return call.call();}};
        task.setOnSucceeded(e->{root.setDisable(false);try{success.accept(task.getValue());}catch(RuntimeException ex){Ui.error(ex.getMessage());}});
        task.setOnFailed(e->{root.setDisable(false);failure.accept(task.getException());});
        worker.execute(task);
    }
    private <T> void background(Callable<T> call,Consumer<T> success) {background(call,success,error->Ui.error(error.getMessage()));}
}

package id.ac.restoflow.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.BiConsumer;

public final class LoginView extends StackPane {
    public LoginView(boolean configured,BiConsumer<String,String> signIn,Runnable configure){
        getStyleClass().add("login-screen");setPadding(new Insets(32));
        Label brand=Ui.label("RestoFlow");brand.getStyleClass().add("login-brand");
        Label introduction=Ui.label("Meja kerja\nrestoran Anda.");introduction.getStyleClass().add("login-introduction");
        Label description=Ui.label("Catat pesanan, teruskan ke dapur,\nlalu selesaikan pembayaran.");description.getStyleClass().add("login-description");
        Region gap=new Region();VBox.setVgrow(gap,Priority.ALWAYS);
        Label scope=Ui.label("Menu · Meja · Pesanan · Nota");scope.getStyleClass().add("muted");
        VBox context=new VBox(28,brand,introduction,description,gap,scope);context.getStyleClass().add("login-context");
        context.setMinWidth(300);context.setPrefWidth(400);context.setMaxWidth(440);
        Label title=Ui.heading("Masuk ke restoran");title.setId("login-title");
        Label subtitle=Ui.label("Gunakan akun yang diberikan oleh admin.");subtitle.getStyleClass().add("muted");
        TextField email=new TextField();email.setId("login-email");email.setPromptText("Email akun restoran");
        PasswordField password=new PasswordField();password.setId("login-password");password.setPromptText("Password akun");
        Label emailLabel=Ui.label("Email");emailLabel.setLabelFor(email);
        Label passwordLabel=Ui.label("Password");passwordLabel.setLabelFor(password);
        Label validation=Ui.label("");validation.setId("login-validation");validation.getStyleClass().add("validation-message");
        validation.setVisible(false);validation.setManaged(false);
        Button login=Ui.button("Masuk",()->{
            if(email.getText().isBlank()||password.getText().isBlank()){
                validation.setText("Isi email dan password untuk masuk.");validation.setVisible(true);validation.setManaged(true);
                (email.getText().isBlank()?email:password).requestFocus();return;
            }
            validation.setVisible(false);validation.setManaged(false);
            String secret=password.getText();password.clear();signIn.accept(email.getText().trim(),secret);
        });login.setId("login-submit");login.getStyleClass().add("primary");login.setMaxWidth(Double.MAX_VALUE);login.setDefaultButton(true);
        VBox inputs=new VBox(8,emailLabel,email,passwordLabel,password);VBox.setMargin(passwordLabel,new Insets(10,0,0,0));
        Button connection=Ui.button("Atur koneksi database",configure);connection.setId("login-connection");connection.getStyleClass().add("text-button");
        Label connectionState=Ui.label(configured?"Koneksi tersimpan di komputer ini.":"Belum ada koneksi. Atur database sebelum masuk.");connectionState.getStyleClass().add("muted");
        Label privacy=Ui.label("Password tidak disimpan di komputer ini.");privacy.getStyleClass().add("muted");
        VBox form=new VBox(18,title,subtitle,inputs,validation,login,new Separator(),connectionState,connection,privacy);
        form.getStyleClass().add("login-form");form.setMaxWidth(470);form.setAlignment(Pos.CENTER_LEFT);
        StackPane formArea=new StackPane(form);formArea.setPadding(new Insets(32,40,32,40));HBox.setHgrow(formArea,Priority.ALWAYS);
        HBox panel=new HBox(context,formArea);panel.getStyleClass().add("login-panel");panel.setMaxSize(1060,560);
        getChildren().add(panel);
    }
}

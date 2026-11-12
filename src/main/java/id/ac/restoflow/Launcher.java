package id.ac.restoflow;

import javafx.application.Application;

/** Entry point terpisah agar launcher desktop dapat memuat runtime JavaFX dari classpath. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Application.launch(RestoFlowApp.class, args);
    }
}

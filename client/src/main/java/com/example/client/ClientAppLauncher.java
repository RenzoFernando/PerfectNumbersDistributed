package com.example.client;

import javafx.application.Application;

public class ClientAppLauncher {
    public static void main(String[] args) {
        // Lanza la aplicación JavaFX. ClientApp ahora extenderá javafx.application.Application
        Application.launch(ClientApp.class, args);
    }
}

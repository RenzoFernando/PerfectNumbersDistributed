// --- Archivo: client/src/main/java/com/example/client/ClientAppLauncher.java ---
package com.example.client;

import javafx.application.Application; // Clase base de JavaFX para lanzar aplicaciones

// Lanzador de la aplicación cliente JavaFX para búsqueda de números perfectos
public class ClientAppLauncher {
    // Método principal que arranca la app JavaFX
    public static void main(String[] args) {
        // Ejecuta la aplicación definiendo la clase que extiende Application (ClientApp)
        Application.launch(ClientApp.class, args);
    }
}
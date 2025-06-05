// --- Archivo: client/src/main/java/com/example/client/ClientAppLauncher.java ---
package com.example.client;

import javafx.application.Application;

/**
 * Clase lanzadora para la aplicación JavaFX del cliente.
 * Su único propósito es llamar a Application.launch() con la clase principal de JavaFX (ClientApp).
 * Esta separación a veces ayuda a evitar problemas con la carga de módulos de JavaFX en
 * ciertos entornos o cuando se empaqueta la aplicación.
 */
public class ClientAppLauncher {
    /**
     * Punto de entrada principal que lanza la aplicación JavaFX.
     * @param args Argumentos de la línea de comandos pasados a la aplicación.
     */
    public static void main(String[] args) {
        // Lanza la aplicación JavaFX. ClientApp debe extender javafx.application.Application.
        Application.launch(ClientApp.class, args);
    }
}
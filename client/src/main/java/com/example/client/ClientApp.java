package com.example.client;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.Util;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class ClientApp extends Application {

    private Communicator communicator;
    private ClientViewController controller;

    @Override
    public void init() throws Exception {
        super.init();
        // Inicializar el Communicator de Ice.
        // Los argumentos de línea de comandos (si los pasaste a Application.launch)
        // están disponibles a través de getParameters().getRaw().
        // Sin embargo, para simplificar, leeremos client.properties directamente.
        try {
            communicator = Util.initialize(new String[]{}, "client.properties");
            System.out.println("[CLIENTE-APP] Communicator Ice inicializado.");
        } catch (Exception e) {
            System.err.println("[CLIENTE-APP] Error inicializando Communicator Ice: " + e.getMessage());
            e.printStackTrace();
            // Si falla la inicialización de Ice, podríamos querer terminar la app JavaFX
            Platform.exit();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        if (communicator == null) {
            System.err.println("[CLIENTE-APP] Communicator no inicializado, no se puede iniciar la UI.");
            Platform.exit();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/com/example/client/client-view.fxml")));
            Parent root = loader.load();

            controller = loader.getController();
            if (controller != null) {
                controller.setCommunicator(communicator);
                controller.setApp(this); // Pasar referencia de la app al controlador
            } else {
                System.err.println("[CLIENTE-APP] Error: No se pudo obtener el controlador FXML.");
                Platform.exit();
                return;
            }

            primaryStage.setTitle("Cliente - Búsqueda de Números Perfectos");
            primaryStage.setScene(new Scene(root, 600, 450));
            primaryStage.setOnCloseRequest(event -> {
                // Asegurarse de que el communicator se cierre al cerrar la ventana.
                // El método stop() de la aplicación se llamará automáticamente.
                System.out.println("[CLIENTE-APP] Solicitud de cierre de ventana.");
            });
            primaryStage.show();
        } catch (IOException e) {
            System.err.println("[CLIENTE-APP] Error cargando FXML: " + e.getMessage());
            e.printStackTrace(); // Imprime la traza completa para ver la causa raíz
            Platform.exit();
        } catch (Exception e) {
            System.err.println("[CLIENTE-APP] Error inesperado al iniciar la UI: " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
        }
    }

    @Override
    public void stop() throws Exception {
        System.out.println("[CLIENTE-APP] Deteniendo la aplicación JavaFX.");
        if (controller != null) {
            controller.shutdownIce(); // Llamar a un método en el controlador para limpiar Ice.
        }
        if (communicator != null) {
            try {
                communicator.destroy();
                System.out.println("[CLIENTE-APP] Communicator Ice destruido.");
            } catch (Exception e) {
                System.err.println("[CLIENTE-APP] Error destruyendo Communicator Ice: " + e.getMessage());
                e.printStackTrace();
            }
        }
        super.stop();
        System.out.println("[CLIENTE-APP] Aplicación detenida.");
        // Forzar salida si es necesario, ya que los hilos de Ice podrían seguir activos.
        System.exit(0);
    }

    // Método para que el controlador pueda solicitar el cierre de la aplicación
    public void requestShutdown() {
        Platform.runLater(Platform::exit);
    }
}

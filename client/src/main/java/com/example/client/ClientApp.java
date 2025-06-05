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

    // Comunicator de ICE para manejar la comunicación remota
    private Communicator communicator;
    // Referencia al controlador de la vista JavaFX
    private ClientViewController controller;

    @Override
    public void init() throws Exception {
        super.init();
        // Inicializar el Communicator de Ice
        // para simplificar, leeremos client.properties directamente
        try {
            communicator = Util.initialize(new String[]{}, "client.properties");
            System.out.println("[CLIENTE-APP] Communicator Ice inicializado.");
        } catch (Exception e) {
            System.err.println("[CLIENTE-APP] Error inicializando Communicator Ice: " + e.getMessage());
            e.printStackTrace();
            // Si falla la inicialización de Ice, cerramos la app JavaFX porque no podemos continuar
            Platform.exit();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        // Si el communicator no se inicializó, no podemos arrancar la UI
        if (communicator == null) {
            System.err.println("[CLIENTE-APP] Communicator no inicializado, no se puede iniciar la UI.");
            Platform.exit();
            return;
        }

        try {
            // Cargar el archivo FXML para la interfaz gráfica
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/com/example/client/client-view.fxml")));
            Parent root = loader.load();

            // Obtener el controlador asociado al FXML
            controller = loader.getController();
            if (controller != null) {
                // Pasar el communicator al controlador para que maneje llamadas ICE
                controller.setCommunicator(communicator);
                // Pasar referencia de esta aplicación al controlador (por si necesita cerrar la app)
                controller.setApp(this);
            } else {
                System.err.println("[CLIENTE-APP] Error: No se pudo obtener el controlador FXML.");
                Platform.exit();
                return;
            }

            // Configurar y mostrar la ventana principal de JavaFX
            primaryStage.setTitle("Cliente - Búsqueda de Números Perfectos");
            primaryStage.setScene(new Scene(root, 600, 450));
            primaryStage.setOnCloseRequest(event -> {
                // Al cerrar la ventana, se invocará stop() automáticamente
                System.out.println("[CLIENTE-APP] Solicitud de cierre de ventana.");
            });
            primaryStage.show();
        } catch (IOException e) {
            System.err.println("[CLIENTE-APP] Error cargando FXML: " + e.getMessage());
            e.printStackTrace(); // Ayuda a ver la causa raíz del error
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
        // Pedir al controlador que cierre el adapter de ICE antes de destruir el communicator
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
        // Forzar salida si algún hilo de ICE queda activo
        System.exit(0);
    }

    // Permite al controlador solicitar el cierre de la aplicación JavaFX
    public void requestShutdown() {
        Platform.runLater(Platform::exit);
    }
}

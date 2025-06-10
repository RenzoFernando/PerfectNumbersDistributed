// --- Archivo: client/src/main/java/com/example/client/ClientApp.java ---
package com.example.client;

import com.zeroc.Ice.Communicator; // Componente central de ICE para la comunicación.
import com.zeroc.Ice.Util; // Utilidades de ICE, como initialize para crear el Communicator
import javafx.application.Application; // Clase base para aplicaciones JavaFX
import javafx.application.Platform; // Para ejecutar operaciones en el hilo de la UI de JavaFX
import javafx.fxml.FXMLLoader; // Para cargar la interfaz de usuario desde un archivo FXML
import javafx.scene.Parent; // Nodo raíz de la escena en JavaFX
import javafx.scene.Scene; // Contenedor para todos los contenidos de una ventana
import javafx.stage.Stage; // La ventana principal de la aplicación JavaFX
import java.io.IOException; // Para manejar errores de carga de FXML
import java.util.Objects; // Util para validar recursos

/**
 * Aplicación cliente JavaFX para búsqueda distribuida de números perfectos.
 * Inicializa Ice y carga la interfaz desde un archivo FXML.
 */
public class ClientApp extends Application {
    private Communicator communicator; // Ice communicator para llamadas remotas
    private ClientViewController controller; // Controlador de la interfaz

    /**
     * Método del ciclo de vida de JavaFX, llamado antes de start().
     * Aquí se inicializa el communicator de ICE. Si falla, la aplicación se cierra.
     * @throws Exception Si ocurre un error durante la inicialización de ICE.
     */
    @Override
    public void init() throws Exception {
        super.init(); // Llamar al init de la superclase
        try {
            System.out.println("[CLIENTE-APP] init(): Iniciando communicator.initialize()...");
            communicator = Util.initialize(new String[]{}, "client.properties"); // Cargar configuración de Ice
            System.out.println("[CLIENTE-APP] init(): Communicator Ice inicializado exitosamente.");
        } catch (Exception e) {
            // Si hay un error al inicializar ICE (ej. archivo de propiedades no encontrado,
            // problemas de configuración de red de Ice), se loguea el error y se cierra la aplicación.
            System.err.println("[CLIENTE-APP] init(): FATAL - Error inicializando Communicator Ice: " + e.getMessage());
            e.printStackTrace();
            Platform.exit(); // Cierra la aplicación JavaFX desde cualquier hilo
        }
    }

    /**
     * Método del ciclo de vida de JavaFX, llamado después de init().
     * Configura y muestra la ventana principal (Stage) de la aplicación, cargando la UI desde FXML
     * @param primaryStage El Stage principal proporcionado por JavaFX para la aplicación.
     */
    @Override
    public void start(Stage primaryStage) {
        // Verificar que el communicator de ICE se haya inicializado correctamente en init()
        if (communicator == null) {
            System.err.println("[CLIENTE-APP] start(): Communicator no fue inicializado en init(), no se puede iniciar la UI. Saliendo.");
            Platform.exit(); // Salir si no hay communicator, ya que la app no puede funcionar
            return;
        }
        try {
            // Cargar el archivo FXML que define la interfaz gráfica
            System.out.println("[CLIENTE-APP] start(): Intentando cargar FXML desde: /com/example/client/client-view.fxml");
            // Se usa Objects.requireNonNull para que lance NullPointerException si getResource devuelve null,
            // lo que indica que el archivo FXML no se encontró en la ruta especificada
            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/com/example/client/client-view.fxml"),
                    "Archivo FXML '/com/example/client/client-view.fxml' no encontrado en src/main/resources. Verifique la ruta."
            ));
            System.out.println("[CLIENTE-APP] start(): FXMLLoader instanciado. Intentando loader.load() para cargar el FXML...");
            Parent root = loader.load();  // Leer la definición de la UI
            System.out.println("[CLIENTE-APP] start(): FXML cargado exitosamente (Parent root obtenido).");

            controller = loader.getController(); // Obtener el controlador asociado al FXML
            if (controller != null) {
                System.out.println("[CLIENTE-APP] start(): Controlador FXML (ClientViewController) obtenido exitosamente.");
                // Esto permite al controlador realizar llamadas de Ice y, si es necesario, cerrar la app.
                controller.setCommunicator(communicator); // Pasar communicator al controlador
                controller.setApp(this); // Pasar referencia de la app al controlador
                System.out.println("[CLIENTE-APP] start(): Communicator y referencia de App pasados al controlador.");
            } else {
                // Error crítico si el controlador no se puede obtener. Usualmente indica un problema en el FXML
                System.err.println("[CLIENTE-APP] start(): FATAL - No se pudo obtener el controlador FXML (loader.getController() devolvió null).");
                Platform.exit();
                return;
            }

            // Configurar el Stage (ventana principal).
            primaryStage.setTitle("Cliente - Búsqueda Distribuida de Números Perfectos");
            Scene scene = new Scene(root, 700, 620); // Ajustar altura para más logs
            primaryStage.setScene(scene);
            System.out.println("[CLIENTE-APP] start(): Scene creada y asignada al Stage.");

            // Definir qué hacer cuando el usuario intenta cerrar la ventana
            // El método stop() de esta clase se llamará automáticamente como parte del ciclo de vida de JavaFX
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("[CLIENTE-APP] Solicitud de cierre de ventana recibida (se procederá a llamar a stop()).");
                // No es necesario llamar a controller.requestShutdown() aquí, stop() se encarga
            });

            // Mostrar la ventana.
            System.out.println("[CLIENTE-APP] start(): Mostrando primaryStage (invocando primaryStage.show())...");
            primaryStage.show();
            System.out.println("[CLIENTE-APP] start(): primaryStage mostrado. La UI debería ser visible ahora.");

        } catch (NullPointerException npe) {
            // Captura específica si Objects.requireNonNull falla debido a que getResource es null.
            System.err.println("[CLIENTE-APP] start(): FATAL - NullPointerException al intentar cargar FXML. " +
                    "Es muy probable que el archivo FXML no se encontrara en la ruta especificada dentro de 'src/main/resources'.");
            npe.printStackTrace();
            Platform.exit();
        } catch (IOException e) {
            // Captura javafx.fxml.LoadException y otras IOExceptions.
            System.err.println("[CLIENTE-APP] start(): FATAL - IOException (o javafx.fxml.LoadException) al cargar FXML: " + e.getMessage());
            // Imprimir la causa raíz ayuda mucho a diagnosticar problemas en el FXML o en el método initialize() del controlador.
            if (e.getCause() != null) {
                System.err.println("[CLIENTE-APP] start(): Causa Raíz del LoadException: " + e.getCause().toString());
                e.getCause().printStackTrace();
            } else {
                e.printStackTrace();
            }
            Platform.exit();
        } catch (Exception e) {
            // Captura cualquier otra excepción inesperada durante el inicio de la UI.
            System.err.println("[CLIENTE-APP] start(): FATAL - Error inesperado y no manejado al iniciar la UI: " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
        }
    }

    /**
     * Método del ciclo de vida de JavaFX. Se llama cuando la aplicación se cierra
     * Es el lugar adecuado para liberar recursos importantes como el communicator de ICE
     * @throws Exception Si ocurre un error durante el proceso de cierre.
     */
    @Override
    public void stop() throws Exception {
        System.out.println("[CLIENTE-APP] STOP: Método stop() de la aplicación JavaFX invocado.");

        // Pedir al controlador que libere sus recursos de ICE (principalmente el ObjectAdapter)
        if (controller != null) {
            System.out.println("[CLIENTE-APP] STOP: Llamando a controller.shutdownIce() para liberar su adaptador de notificaciones.");
            controller.shutdownIce();
        }

        // Destruir el communicator principal de Ice para cerrar conexiones
        if (communicator != null) {
            try {
                System.out.println("[CLIENTE-APP] STOP: Destruyendo el communicator principal de Ice...");
                communicator.destroy(); // Cierra todas las conexiones e hilos de Ice
                System.out.println("[CLIENTE-APP] STOP: Communicator Ice destruido exitosamente.");
            } catch (Exception e) {
                System.err.println("[CLIENTE-APP] STOP: Error al intentar destruir el Communicator Ice: " + e.getMessage());
                e.printStackTrace();
            }
        }
        super.stop(); // Llama al método stop de la superclase Application
        System.out.println("[CLIENTE-APP] STOP: Aplicación JavaFX y recursos de Ice (idealmente) detenidos.");

        System.out.println("[CLIENTE-APP] STOP: Llamando a System.exit(0) para asegurar cierre de JVM.");
        System.exit(0); // Forzar fin de la aplicación
    }

    /**
     * Permite al controlador solicitar el cierre de la aplicación.
     */
    public void requestShutdown() {
        System.out.println("[CLIENTE-APP] requestShutdown() llamado por el controlador. Solicitando cierre de la aplicación a JavaFX.");
        try {
            stop(); // Llama a nuestra lógica de limpieza y luego a System.exit()
        } catch (Exception e) {
            System.err.println("[CLIENTE-APP] requestShutdown: Excepción al llamar a stop(): " + e.getMessage());
            e.printStackTrace();
            System.exit(1); // Salir con error si stop() falla
        }
    }
}
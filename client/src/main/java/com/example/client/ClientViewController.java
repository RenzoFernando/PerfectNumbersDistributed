package com.example.client;

import perfectNumbersApp.*;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.LocalException;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
// CORRECCIÓN: Asegurar la importación correcta para TextArea de JavaFX
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
// NO DEBE HABER: import java.awt.TextArea;

public class ClientViewController {

    @FXML private TextField startRangeField; // Campo de texto para ingresar el inicio del rango
    @FXML private TextField endRangeField; // Campo de texto para ingresar el fin del rango
    @FXML private Button searchButton; // Botón para iniciar la búsqueda de números perfectos
    @FXML private TextArea resultsTextArea; // Área de texto para mostrar resultados y mensajes

    private Communicator communicator; // Comunicator de ICE para llamadas remotas
    private MasterServicePrx masterServicePrx; // Proxy al servicio del Maestro
    private ClientNotifierPrx clientNotifierPrx; // Proxy para que el Maestro notifique al Cliente
    private ObjectAdapter notifierAdapter; // Adaptador para exponer el ClientNotifier
    // private ClientNotifierI notifierServant; // Se instancia dentro de initializeIceProxies
    private ClientApp clientApp; // Referencia a la aplicación principal (para cerrar)

    // Método llamado desde ClientApp para pasar el Communicator y inicializar proxies
    public void setCommunicator(Communicator communicator) {
        this.communicator = communicator;
        initializeIceProxies(); // Configurar proxies una vez que tenemos communicator
    }

    // Guarda la referencia a la aplicación principal para luego poder solicitar cierre
    public void setApp(ClientApp app) {
        this.clientApp = app;
    }

    @FXML
    public void initialize() {
        // Valores por defecto que se muestran al iniciar la vista
        startRangeField.setText("1");
        endRangeField.setText("10000");
    }

    // Inicializa los proxies de ICE: uno al Maestro y otro para recibir notificaciones
    private void initializeIceProxies() {
        if (this.communicator == null) {
            logToTextArea("[ERROR-CTRL] El Communicator de Ice no está inicializado.");
            return;
        }
        try {
            // Obtener proxy del Maestro
            ObjectPrx baseMasterPrx = communicator.propertyToProxy("MasterService.Proxy");
            if (baseMasterPrx == null) {
                // Si no está configurado en client.properties, intentar localhost:10000
                logToTextArea("[ERROR-CTRL] Proxy del maestro (MasterService.Proxy) no configurado en client.properties. Intentando localhost:10000");
                baseMasterPrx = communicator.stringToProxy("MasterService:default -h localhost -p 10000");
            }

            if (baseMasterPrx == null) {
                logToTextArea("[ERROR-CTRL] No se pudo obtener el proxy del maestro.");
                Platform.runLater(() -> searchButton.setDisable(true));
                return;
            }

            // Verificar que el proxy sea correcto y convertirlo a MasterServicePrx
            masterServicePrx = MasterServicePrx.checkedCast(baseMasterPrx);
            if (masterServicePrx == null) {
                logToTextArea("[ERROR-CTRL] Proxy del maestro inválido.");
                Platform.runLater(() -> searchButton.setDisable(true));
                return;
            }
            logToTextArea("[CLIENTE-CTRL] Proxy del maestro obtenido correctamente.");

            // Adaptador y Worker para ClientNotifier
            // El adaptador usará los endpoints configurados en client.properties (ClientNotifierAdapter.Endpoints)
            notifierAdapter = communicator.createObjectAdapter("ClientNotifierAdapter");
            ClientNotifierI notifierServant = new ClientNotifierI(this); // El Worker recibe referencia al controlador
            ObjectPrx servantProxy = notifierAdapter.addWithUUID(notifierServant); // Registrar worker con ID único
            clientNotifierPrx = ClientNotifierPrx.checkedCast(servantProxy); // Convertir a proxy adecuado
            notifierAdapter.activate(); // Activar el adaptador para que reciba llamadas entrantes
            logToTextArea("[CLIENTE-CTRL] Adaptador ClientNotifier activado.");

        } catch (LocalException e) {
            // Errores específicos de ICE (p. ej. problemas de red o configuración)
            logToTextArea("[ERROR-CTRL] Error de Ice inicializando proxies: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> searchButton.setDisable(true));
        } catch (Exception e) {
            // Cualquier otro error inesperado
            logToTextArea("[ERROR-CTRL] Error inesperado inicializando proxies: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> searchButton.setDisable(true));
        }
    }

    // Método invocado cuando el usuario presiona el botón "Buscar"
    @FXML
    private void handleSearchAction() {
        // Verificar que los proxies están listos antes de hacer la llamada remota
        if (masterServicePrx == null || clientNotifierPrx == null) {
            logToTextArea("[ERROR-CTRL] Los proxies de Ice no están listos. No se puede buscar.");
            return;
        }

        long start, end;
        try {
            // Intentar parsear los valores ingresados en los campos de texto
            start = Long.parseLong(startRangeField.getText());
            end = Long.parseLong(endRangeField.getText());
        } catch (NumberFormatException e) {
            logToTextArea("[ERROR-CTRL] Rango inválido. Por favor, ingrese números válidos.");
            return;
        }

        // Validar que el rango tenga sentido (inicio > 0, fin > 0, fin >= inicio)
        if (start <= 0 || end <= 0 || start > end) {
            logToTextArea("[ERROR-CTRL] Rango inválido. Inicio debe ser > 0, Fin debe ser > 0, y Fin >= Inicio.");
            return;
        }

        // Crear objeto Range para enviar al Maestro
        Range jobRange = new Range(start, end);
        logToTextArea("[CLIENTE-CTRL] Solicitando números perfectos en el rango [" + jobRange.start + ", " + jobRange.end + "]");
        searchButton.setDisable(true); // Deshabilitar el botón para evitar varias solicitudes al tiempo

        try {
            // Llamada asíncrona al Maestro. El Maestro notificará a través de clientNotifierPrx
            masterServicePrx.findPerfectNumbersInRangeAsync(jobRange, clientNotifierPrx)
                    .whenCompleteAsync((result, ex) -> {
                        if (ex != null) {
                            // Llamada asíncrona al Maestro. El Maestro notificará a través de clientNotifierPrx
                            Platform.runLater(() -> {
                                logToTextArea("[ERROR-CTRL] Error al enviar la solicitud al maestro: " + ex.getMessage());
                                searchButton.setDisable(false); // Habilitar el botón nuevamente
                            });
                            ex.printStackTrace();
                        } else {
                            // Confirmar al usuario que la petición fue enviada
                            Platform.runLater(() -> {
                                logToTextArea("[CLIENTE-CTRL] Petición enviada al maestro. Esperando notificación...");
                            });
                        }
                    }, Platform::runLater);
        } catch (Exception e) {
            // Capturar cualquier excepción que ocurra al invocar el método remoto
            logToTextArea("[ERROR-CTRL] Excepción al llamar a findPerfectNumbersInRangeAsync: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> searchButton.setDisable(false)); // Habilitar botón si hay error
        }
    }

    // Agrega texto al TextArea de resultados de forma segura en el hilo de la UI
    public void appendResults(String message) {
        Platform.runLater(() -> {
            resultsTextArea.appendText(message + "\n");
        });
    }

    // Habilita el botón de búsqueda cuando el trabajo ha finalizado
    public void jobFinished() {
        Platform.runLater(() -> {
            searchButton.setDisable(false);
        });
    }

    // Método genérico para escribir logs en el TextArea o consola si la UI no está lista
    public void logToTextArea(String message) {
        if (resultsTextArea != null) {
            Platform.runLater(() -> resultsTextArea.appendText(message + "\n"));
        } else {
            // Si el TextArea no está disponible, imprimir en consola
            System.out.println("Log (TextArea nulo): " + message); // Fallback si la UI no está lista
        }
    }

    // Métodos que cierra el adaptador de notificación de ICE cuando la app se cierra
    public void shutdownIce() {
        logToTextArea("[CLIENTE-CTRL] Iniciando cierre de Ice desde el controlador.");
        if (notifierAdapter != null) {
            try {
                notifierAdapter.destroy(); // Destruir adaptador para liberar puertos y recursos
                logToTextArea("[CLIENTE-CTRL] Adaptador ClientNotifier destruido.");
            } catch (Exception e) {
                logToTextArea("[ERROR-CTRL] Error destruyendo ClientNotifierAdapter: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

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

    @FXML private TextField startRangeField;
    @FXML private TextField endRangeField;
    @FXML private Button searchButton;
    @FXML private TextArea resultsTextArea; // Debe ser javafx.scene.control.TextArea

    private Communicator communicator;
    private MasterServicePrx masterServicePrx;
    private ClientNotifierPrx clientNotifierPrx;
    private ObjectAdapter notifierAdapter;
    // private ClientNotifierI notifierServant; // Se instancia dentro de initializeIceProxies
    private ClientApp clientApp; // Referencia a la aplicación principal

    public void setCommunicator(Communicator communicator) {
        this.communicator = communicator;
        initializeIceProxies();
    }

    public void setApp(ClientApp app) {
        this.clientApp = app;
    }

    @FXML
    public void initialize() {
        // Puedes poner valores por defecto aquí si lo deseas
        startRangeField.setText("1");
        endRangeField.setText("10000");
    }

    private void initializeIceProxies() {
        if (this.communicator == null) {
            logToTextArea("[ERROR-CTRL] El Communicator de Ice no está inicializado.");
            return;
        }
        try {
            // Proxy del Maestro
            ObjectPrx baseMasterPrx = communicator.propertyToProxy("MasterService.Proxy");
            if (baseMasterPrx == null) {
                logToTextArea("[ERROR-CTRL] Proxy del maestro (MasterService.Proxy) no configurado en client.properties. Intentando localhost:10000");
                baseMasterPrx = communicator.stringToProxy("MasterService:default -h localhost -p 10000");
            }

            if (baseMasterPrx == null) {
                logToTextArea("[ERROR-CTRL] No se pudo obtener el proxy del maestro.");
                Platform.runLater(() -> searchButton.setDisable(true));
                return;
            }
            masterServicePrx = MasterServicePrx.checkedCast(baseMasterPrx);
            if (masterServicePrx == null) {
                logToTextArea("[ERROR-CTRL] Proxy del maestro inválido.");
                Platform.runLater(() -> searchButton.setDisable(true));
                return;
            }
            logToTextArea("[CLIENTE-CTRL] Proxy del maestro obtenido correctamente.");

            // Adaptador y Servant para ClientNotifier
            // Usar un nombre de adaptador vacío para que Ice elija los endpoints del client.properties (ClientNotifierAdapter.Endpoints)
            // o cree uno sobre la marcha si no está especificado.
            notifierAdapter = communicator.createObjectAdapter("ClientNotifierAdapter");
            ClientNotifierI notifierServant = new ClientNotifierI(this); // Pasar referencia del controlador al servant
            ObjectPrx servantProxy = notifierAdapter.addWithUUID(notifierServant);
            clientNotifierPrx = ClientNotifierPrx.checkedCast(servantProxy);
            notifierAdapter.activate();
            logToTextArea("[CLIENTE-CTRL] Adaptador ClientNotifier activado.");

        } catch (LocalException e) {
            logToTextArea("[ERROR-CTRL] Error de Ice inicializando proxies: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> searchButton.setDisable(true));
        } catch (Exception e) {
            logToTextArea("[ERROR-CTRL] Error inesperado inicializando proxies: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> searchButton.setDisable(true));
        }
    }

    @FXML
    private void handleSearchAction() {
        if (masterServicePrx == null || clientNotifierPrx == null) {
            logToTextArea("[ERROR-CTRL] Los proxies de Ice no están listos. No se puede buscar.");
            return;
        }

        long start, end;
        try {
            start = Long.parseLong(startRangeField.getText());
            end = Long.parseLong(endRangeField.getText());
        } catch (NumberFormatException e) {
            logToTextArea("[ERROR-CTRL] Rango inválido. Por favor, ingrese números válidos.");
            return;
        }

        if (start <= 0 || end <= 0 || start > end) {
            logToTextArea("[ERROR-CTRL] Rango inválido. Inicio debe ser > 0, Fin debe ser > 0, y Fin >= Inicio.");
            return;
        }

        Range jobRange = new Range(start, end);
        logToTextArea("[CLIENTE-CTRL] Solicitando números perfectos en el rango [" + jobRange.start + ", " + jobRange.end + "]");
        searchButton.setDisable(true); // Deshabilitar botón mientras se procesa

        try {
            masterServicePrx.findPerfectNumbersInRangeAsync(jobRange, clientNotifierPrx)
                    .whenCompleteAsync((result, ex) -> {
                        if (ex != null) {
                            Platform.runLater(() -> {
                                logToTextArea("[ERROR-CTRL] Error al enviar la solicitud al maestro: " + ex.getMessage());
                                searchButton.setDisable(false);
                            });
                            ex.printStackTrace();
                        } else {
                            Platform.runLater(() -> {
                                logToTextArea("[CLIENTE-CTRL] Petición enviada al maestro. Esperando notificación...");
                            });
                        }
                    }, Platform::runLater);
        } catch (Exception e) {
            logToTextArea("[ERROR-CTRL] Excepción al llamar a findPerfectNumbersInRangeAsync: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> searchButton.setDisable(false));
        }
    }

    public void appendResults(String message) {
        Platform.runLater(() -> {
            resultsTextArea.appendText(message + "\n");
        });
    }

    public void jobFinished() {
        Platform.runLater(() -> {
            searchButton.setDisable(false);
        });
    }

    public void logToTextArea(String message) {
        if (resultsTextArea != null) {
            Platform.runLater(() -> resultsTextArea.appendText(message + "\n"));
        } else {
            System.out.println("Log (TextArea nulo): " + message); // Fallback si la UI no está lista
        }
    }

    public void shutdownIce() {
        logToTextArea("[CLIENTE-CTRL] Iniciando cierre de Ice desde el controlador.");
        if (notifierAdapter != null) {
            try {
                notifierAdapter.destroy();
                logToTextArea("[CLIENTE-CTRL] Adaptador ClientNotifier destruido.");
            } catch (Exception e) {
                logToTextArea("[ERROR-CTRL] Error destruyendo ClientNotifierAdapter: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

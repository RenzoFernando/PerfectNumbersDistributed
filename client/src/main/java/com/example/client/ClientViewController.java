package com.example.client;

import perfectNumbersApp.*; // Clases generadas por ICE (Range, MasterServicePrx, etc.)
import com.zeroc.Ice.Communicator; // Comunicador principal de ICE
import com.zeroc.Ice.LocalException; // Excepciones locales de ICE (ej. problemas de conexión)
import com.zeroc.Ice.ObjectAdapter; // Para crear servants que reciben llamadas
import com.zeroc.Ice.ObjectPrx; // Proxy base de ICE
import com.zeroc.Ice.Util; // Utilidades de Ice

import javafx.animation.FadeTransition; // Para animaciones de fundido
import javafx.application.Platform; // Para ejecutar código en el hilo de la UI de JavaFX
import javafx.fxml.FXML; // Para enlazar con elementos del archivo FXML
import javafx.scene.Node; // Nodo base para elementos de UI (usado en animación)
import javafx.scene.control.Button; // Botón de JavaFX
import javafx.scene.control.Label; // Etiqueta de JavaFX
import javafx.scene.control.TextArea; // Área de texto de JavaFX
import javafx.scene.control.TextField; // Campo de texto de JavaFX
import javafx.util.Duration; // Para especificar la duración de las animaciones

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
// import java.util.Map; // No se usa actualmente, se puede quitar si no hay planes


public class ClientViewController {
    // --- Campos FXML Inyectados (@FXML) ---
    @FXML private TextField startRangeField;
    @FXML private TextField endRangeField;
    @FXML private TextField numWorkersField;
    @FXML private Button searchButton;
    @FXML private Button refreshStatusButton;
    @FXML private Button clearLogButton;
    @FXML private Label masterStatusLabel;
    @FXML private Label availableWorkersLabel;
    @FXML private TextArea resultsTextArea;

    // --- Componentes de ICE y Estado ---
    private Communicator communicator;
    private MasterServicePrx masterServicePrx;
    private ClientNotifierPrx clientNotifierPrx;
    private ObjectAdapter notifierAdapter;
    private ClientApp clientApp;

    private boolean masterConnected = false;
    private int lastKnownWorkerCount = 0;
    private long clientRequestStartTime;

    // --- Valores por Defecto para la UI ---
    private static final int DEFAULT_NUM_WORKERS = 1;
    private static final String DEFAULT_START_RANGE = "1";
    private static final String DEFAULT_END_RANGE = "100000";

    public void setCommunicator(Communicator communicator) {
        this.communicator = communicator;
        if (this.communicator != null) {
            System.out.println("[CLIENTE-CTRL] setCommunicator: Communicator recibido. Iniciando componentes de Ice en hilo de fondo...");
            new Thread(this::initializeIceComponents).start();
        } else {
            logToUIAndConsole("[ERROR-CTRL] setCommunicator: Communicator es NULO. No se pueden inicializar proxies.", true);
            Platform.runLater(() -> {
                updateStatusLabels("Maestro: Error crítico (Communicator nulo)", "error",
                        "Workers disponibles: Error", "error");
                searchButton.setDisable(true);
                refreshStatusButton.setDisable(true);
            });
        }
    }

    public void setApp(ClientApp app) {
        this.clientApp = app;
    }

    @FXML
    public void initialize() {
        System.out.println("[CLIENTE-CTRL] Controlador FXML (ClientViewController): método initialize() llamado por FXMLLoader.");
        startRangeField.setText(DEFAULT_START_RANGE);
        endRangeField.setText(DEFAULT_END_RANGE);
        numWorkersField.setText(String.valueOf(DEFAULT_NUM_WORKERS));
        logToUIAndConsole("[CLIENTE-CTRL] UI: Campos de entrada inicializados con valores por defecto.", false);

        searchButton.setDisable(true);
        refreshStatusButton.setDisable(true);
        updateStatusLabels("Maestro: Desconocido", "normal",
                "Workers disponibles: N/A", "normal");

        // Añadir escuchador para tecla Enter en los campos de texto principales
        // para simular un clic en el botón "Buscar" si está habilitado.
        startRangeField.setOnKeyPressed(event -> { if (event.getCode().toString().equals("ENTER") && !searchButton.isDisabled()) handleSearchAction(); });
        endRangeField.setOnKeyPressed(event -> { if (event.getCode().toString().equals("ENTER") && !searchButton.isDisabled()) handleSearchAction(); });
        numWorkersField.setOnKeyPressed(event -> { if (event.getCode().toString().equals("ENTER") && !searchButton.isDisabled()) handleSearchAction(); });
    }

    private void initializeIceComponents() {
        System.out.println("[ICE-INIT-THREAD] Hilo de inicialización de componentes Ice del cliente iniciado.");
        if (this.communicator == null) {
            logToUIAndConsole("[ERROR-CTRL] Fallo crítico: Communicator no disponible para inicializar componentes Ice.", true);
            return;
        }
        try {
            logToUIAndConsolePlatform("[ICE-INIT-THREAD] Creando y activando adaptador ClientNotifierAdapter...", false);
            notifierAdapter = communicator.createObjectAdapter("ClientNotifierAdapter");
            ClientNotifierI notifierServant = new ClientNotifierI(this);
            ObjectPrx servantProxy = notifierAdapter.addWithUUID(notifierServant);
            clientNotifierPrx = ClientNotifierPrx.uncheckedCast(servantProxy);
            notifierAdapter.activate();

            logToUIAndConsolePlatform("[CLIENTE-CTRL] Adaptador ClientNotifier local activado en: " + Arrays.toString(clientNotifierPrx.ice_getEndpoints()), false);
            Platform.runLater(() -> {
                refreshStatusButton.setDisable(false);
                logToUI("[CLIENTE-CTRL] Listo. Presione 'Actualizar Estado del Sistema' para conectar con el Maestro.");
                animateNodeBriefly(refreshStatusButton); // Pequeña animación al botón
                handleRefreshStatusAction();
            });
            System.out.println("[ICE-INIT-THREAD] Adaptador ClientNotifier configurado y activado.");

        } catch (LocalException e) {
            String errorMsg = "[ERROR-CTRL] Error local de Ice durante la inicialización del ClientNotifier: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            logToUIAndConsole(errorMsg, true, e);
            Platform.runLater(() -> {
                updateStatusLabels("Maestro: Error Interno Cliente", "error",
                        "Workers: Error", "error");
                searchButton.setDisable(true);
                refreshStatusButton.setDisable(false);
            });
        } catch (Exception e) {
            String errorMsg = "[ERROR-CTRL] Error inesperado durante la inicialización del ClientNotifier: " + e.getMessage();
            logToUIAndConsole(errorMsg, true, e);
            Platform.runLater(() -> {
                updateStatusLabels("Maestro: Error Inesperado Cliente", "error",
                        "Workers: Error", "error");
                searchButton.setDisable(true);
                refreshStatusButton.setDisable(false);
            });
        }
    }

    @FXML
    private void handleRefreshStatusAction() {
        animateNodeBriefly(refreshStatusButton);
        logToUIAndConsolePlatform("[CLIENTE-CTRL] Botón 'Actualizar Estado' presionado. Contactando al Maestro...", false);
        refreshStatusButton.setDisable(true);
        searchButton.setDisable(true);
        updateStatusLabels("Maestro: Conectando...", "normal",
                "Workers: Consultando...", "normal");

        CompletableFuture.supplyAsync(() -> {
            boolean currentMasterConnectedAttempt = false;
            int currentActiveWorkersAttempt = -1;
            String currentMasterProxyInfoAttempt = "No disponible";
            try {
                if (communicator == null) throw new IllegalStateException("Communicator de Ice no está inicializado.");
                ObjectPrx baseMasterPrx = communicator.propertyToProxy("MasterService.Proxy");
                if (baseMasterPrx == null) {
                    logToUIAndConsolePlatform("[WARN-CTRL] MasterService.Proxy no encontrado en client.properties. Usando stringToProxy con localhost:10000.", false);
                    baseMasterPrx = communicator.stringToProxy("MasterService:default -h localhost -p 10000");
                }
                if (baseMasterPrx != null) {
                    masterServicePrx = MasterServicePrx.checkedCast(baseMasterPrx);
                    if (masterServicePrx != null) {
                        masterServicePrx.ice_ping();
                        currentMasterProxyInfoAttempt = masterServicePrx.toString().split("\n")[0];
                        logToUIAndConsolePlatform("[CLIENTE-CTRL] Ping al Maestro (" + currentMasterProxyInfoAttempt + ") exitoso. Obteniendo número de workers...", false);
                        currentActiveWorkersAttempt = masterServicePrx.getActiveWorkerCount();
                        currentMasterConnectedAttempt = true;
                    } else {
                        logToUIAndConsolePlatform("[ERROR-CTRL] El proxy al Maestro es inválido (checkedCast falló o el tipo es incorrecto).", true);
                        masterServicePrx = null;
                    }
                } else {
                    logToUIAndConsolePlatform("[ERROR-CTRL] No se pudo obtener el proxy del Maestro. Verifique 'MasterService.Proxy'.", true);
                    masterServicePrx = null;
                }
            } catch (com.zeroc.Ice.ConnectionRefusedException cre) {
                logToUIAndConsolePlatform("[ERROR-CTRL] Error de Ice: Conexión rechazada por el Maestro. ¿Está el Maestro corriendo? Proxy: " + (masterServicePrx != null ? masterServicePrx.toString().split("\n")[0] : "N/A"), true, cre);
                masterServicePrx = null;
            } catch (com.zeroc.Ice.TimeoutException te) {
                logToUIAndConsolePlatform("[ERROR-CTRL] Error de Ice: Timeout contactando al Maestro. Proxy: " + (masterServicePrx != null ? masterServicePrx.toString().split("\n")[0] : "N/A"), true, te);
                masterServicePrx = null;
            } catch (com.zeroc.Ice.ObjectNotExistException one) {
                logToUIAndConsolePlatform("[ERROR-CTRL] Error de Ice: El objeto Maestro no existe en el servidor. Proxy: " + (masterServicePrx != null ? masterServicePrx.toString().split("\n")[0] : "N/A"), true, one);
                masterServicePrx = null;
            } catch (LocalException e) {
                logToUIAndConsolePlatform("[ERROR-CTRL] Error local de Ice al contactar al Maestro: " + e.getClass().getSimpleName() + ". Proxy: " + (masterServicePrx != null ? masterServicePrx.toString().split("\n")[0] : "N/A"), true, e);
                masterServicePrx = null;
            } catch (Exception e) {
                logToUIAndConsolePlatform("[ERROR-CTRL] Error inesperado al contactar al Maestro: " + e.getMessage(), true, e);
                masterServicePrx = null;
            }
            return new MasterConnectionStatus(currentMasterConnectedAttempt, currentActiveWorkersAttempt, currentMasterProxyInfoAttempt);
        }).whenCompleteAsync((status, ex) -> {
            refreshStatusButton.setDisable(false);
            this.masterConnected = status.connected;
            this.lastKnownWorkerCount = status.workerCount;
            if (ex != null) {
                logToUIAndConsole("[ERROR-CTRL] Excepción interna en hilo de actualización de estado: " + ex.getMessage(), true, (Exception)ex);
                updateStatusLabels("Maestro: Error en consulta", "error",
                        "Workers: Error", "error");
                searchButton.setDisable(true);
            } else {
                if (status.connected) {
                    updateStatusLabels("Maestro: Conectado ("+ status.proxyInfo + ")", "success",
                            "Workers activos: " + status.workerCount, (status.workerCount > 0 ? "success" : "normal"));
                    if (status.workerCount > 0) {
                        logToUIAndConsolePlatform("[CLIENTE-CTRL] " + status.workerCount + " worker(s) activo(s). Búsqueda HABILITADA.", false);
                        searchButton.setDisable(false);
                        animateNodeBriefly(searchButton); // Animar botón de búsqueda ahora que está activo
                    } else {
                        logToUIAndConsolePlatform("[CLIENTE-CTRL] Maestro conectado pero no hay workers activos. Búsqueda DESHABILITADA.", false);
                        searchButton.setDisable(true);
                    }
                } else {
                    updateStatusLabels("Maestro: No disponible / Error", "error",
                            "Workers activos: N/A", "error");
                    searchButton.setDisable(true);
                    logToUIAndConsolePlatform("[CLIENTE-CTRL] No se pudo conectar al maestro o obtener información de workers.", false);
                }
            }
        }, Platform::runLater);
    }

    @FXML
    private void handleSearchAction() {
        animateNodeBriefly(searchButton);
        if (!masterConnected || masterServicePrx == null || clientNotifierPrx == null) {
            logToUIAndConsole("[ERROR-CTRL] No se puede buscar: Maestro no conectado o proxies no listos. Presione 'Actualizar Estado'.", true);
            searchButton.setDisable(true);
            return;
        }
        if (lastKnownWorkerCount <= 0) {
            logToUIAndConsole("[ERROR-CTRL] No se puede buscar: No hay workers activos reportados. Presione 'Actualizar Estado'.", true);
            searchButton.setDisable(true);
            return;
        }

        long start, end;
        int workersToUse;
        try {
            start = Long.parseLong(startRangeField.getText());
            end = Long.parseLong(endRangeField.getText());
            workersToUse = Integer.parseInt(numWorkersField.getText());
        } catch (NumberFormatException e) {
            logToUIAndConsole("[ERROR-CTRL] Entradas inválidas. Rango (long) y N° Workers (int) deben ser números.", true);
            animateNodeBriefly(startRangeField); // Animar campos con error
            animateNodeBriefly(endRangeField);
            animateNodeBriefly(numWorkersField);
            return;
        }

        if (start <= 0 || end <= 0 || start > end) {
            logToUIAndConsole("[ERROR-CTRL] Rango inválido: Inicio y Fin deben ser > 0, y Fin >= Inicio.", true);
            animateNodeBriefly(startRangeField);
            animateNodeBriefly(endRangeField);
            return;
        }
        if (workersToUse <= 0) {
            logToUIAndConsole("[ERROR-CTRL] N° de Workers debe ser un entero positivo.", true);
            animateNodeBriefly(numWorkersField);
            return;
        }
        if (workersToUse > 10) {
            logToUIAndConsole("[WARN-CTRL] Solicitó " + workersToUse + " workers. El maestro podría usar un máximo de 10.", false);
        }

        Range jobRange = new Range(start, end);
        logToUIAndConsole("[CLIENTE-CTRL] Solicitando búsqueda en [" + jobRange.start + ", " + jobRange.end + "] usando hasta " + workersToUse + " worker(s).", false);
        searchButton.setDisable(true);
        refreshStatusButton.setDisable(true);

        clientRequestStartTime = System.currentTimeMillis();

        CompletableFuture.runAsync(() -> {
            try {
                masterServicePrx.findPerfectNumbersInRange(jobRange, clientNotifierPrx, workersToUse);
                logToUIAndConsolePlatform("[CLIENTE-CTRL] Petición enviada al Maestro. Esperando notificación de resultados...", false);
            } catch (LocalException e) {
                logToUIAndConsolePlatform("[ERROR-CTRL] Error de Ice al enviar la solicitud de búsqueda: " + e.getClass().getSimpleName() + " - " + e.getMessage(), true, e);
                String errorMsg = "Error de comunicación con el Maestro al enviar la tarea: " + e.getMessage();
                Platform.runLater(() -> {
                    jobFinished();
                    writeTimesToFile(jobRange, new long[0], errorMsg, 0, stopClientTimerAndGetDuration());
                });
            } catch (Exception e) {
                logToUIAndConsolePlatform("[ERROR-CTRL] Error inesperado al enviar la solicitud de búsqueda: " + e.getMessage(), true, e);
                Platform.runLater(this::jobFinished);
            }
        }).exceptionally(ex -> {
            logToUIAndConsolePlatform("[ERROR-CTRL] Excepción en el hilo de envío de la tarea: " + ex.getMessage(), true, (Exception)ex);
            Platform.runLater(this::jobFinished);
            return null;
        });
    }

    @FXML
    private void handleClearLogAction() {
        animateNodeBriefly(clearLogButton);
        if (resultsTextArea != null) {
            resultsTextArea.clear();
            logToUIAndConsole("[CLIENTE-CTRL] Log limpiado por el usuario.", false);
        }
    }

    public long stopClientTimerAndGetDuration() {
        if (clientRequestStartTime > 0) {
            long duration = System.currentTimeMillis() - clientRequestStartTime;
            clientRequestStartTime = 0;
            return duration;
        }
        return 0;
    }

    public void writeTimesToFile(Range range, long[] perfectNumbers, String statusMsg, long masterTime, long clientTime) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("tiempos_ejecucion.txt", true))) {
            writer.println("--- INICIO EJECUCION: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " ---");
            writer.println("Rango Solicitado: [" + range.start + "] - [" + range.end + "]");
            writer.println("Workers Solicitados (Cliente): " + numWorkersField.getText());
            writer.println("Workers Activos Reportados (Previo a la búsqueda): " + lastKnownWorkerCount);
            writer.println("Números Perfectos Encontrados: " + Arrays.toString(perfectNumbers));
            writer.println("Estado Final (Maestro): " + statusMsg);
            writer.println("Tiempo de Procesamiento (Maestro): " + masterTime + " ms");
            writer.println("Tiempo Total de Ejecución (Cliente): " + clientTime + " ms");
            writer.println("--- FIN EJECUCION ---");
            writer.println();
        } catch (IOException e) {
            logToUIAndConsole("[ERROR-CTRL] Error escribiendo tiempos a archivo: " + e.getMessage(), true, e);
        }
    }

    public void appendResults(String message) {
        logToUIAndConsolePlatform(message, false);
        animateNodeBriefly(resultsTextArea); // Animar el área de texto cuando recibe nuevos resultados
    }

    public void jobFinished() {
        Platform.runLater(() -> {
            refreshStatusButton.setDisable(false);
            searchButton.setDisable(true);
            logToUI("[CLIENTE-CTRL] Trabajo finalizado. Presione 'Actualizar Estado' para nueva búsqueda.");
            animateNodeBriefly(refreshStatusButton); // Animar el botón de refresco
        });
    }

    public void logToUI(String message) {
        if (resultsTextArea != null) {
            resultsTextArea.appendText(message + "\n");
        } else {
            System.out.println("logToUI (resultsTextArea NULO): " + message);
        }
    }

    private void logToUIAndConsole(String message, boolean isError) {
        logToUIAndConsole(message, isError, null);
    }

    private void logToUIAndConsole(String message, boolean isError, Exception ex) {
        if (isError) {
            System.err.println(message);
            if (ex != null) ex.printStackTrace(System.err);
        } else {
            System.out.println(message);
        }
        Platform.runLater(() -> logToUI(message));
    }

    private void logToUIAndConsolePlatform(String message, boolean isError) {
        logToUIAndConsolePlatform(message, isError, null);
    }

    private void logToUIAndConsolePlatform(String message, boolean isError, Exception ex) {
        if (isError) {
            System.err.println(message);
            if (ex != null) ex.printStackTrace(System.err);
        } else {
            System.out.println(message);
        }
        Platform.runLater(() -> logToUI(message));
    }

    /**
     * Aplica una animación de "fade in" sutil a un nodo.
     * @param node El nodo a animar.
     */
    private void animateNodeBriefly(Node node) {
        if (node != null) {
            FadeTransition ft = new FadeTransition(Duration.millis(150), node);
            ft.setFromValue(0.7); // Iniciar un poco visible
            ft.setToValue(1.0);
            ft.setCycleCount(2); // Ida y vuelta rápida para un "pulso"
            ft.setAutoReverse(true);
            ft.play();
        }
    }

    /**
     * Actualiza los labels de estado y aplica clases de estilo condicionalmente.
     */
    private void updateStatusLabels(String masterMsg, String masterStatusType, String workerMsg, String workerStatusType) {
        if (masterStatusLabel != null) {
            masterStatusLabel.setText(masterMsg);
            masterStatusLabel.getStyleClass().removeAll("label-status-success", "label-status-error");
            if ("success".equals(masterStatusType)) {
                masterStatusLabel.getStyleClass().add("label-status-success");
            } else if ("error".equals(masterStatusType)) {
                masterStatusLabel.getStyleClass().add("label-status-error");
            }
        }
        if (availableWorkersLabel != null) {
            availableWorkersLabel.setText(workerMsg);
            availableWorkersLabel.getStyleClass().removeAll("label-status-success", "label-status-error");
            if ("success".equals(workerStatusType)) {
                availableWorkersLabel.getStyleClass().add("label-status-success");
            } else if ("error".equals(workerStatusType)) {
                availableWorkersLabel.getStyleClass().add("label-status-error");
            }
        }
    }

    public void shutdownIce() {
        logToUIAndConsolePlatform("[CLIENTE-CTRL] Solicitando cierre de adaptador Ice.", false);
        if (notifierAdapter != null) {
            try {
                notifierAdapter.destroy();
                logToUIAndConsolePlatform("[CLIENTE-CTRL] Adaptador ClientNotifier destruido.", false);
            } catch (Exception e) {
                logToUIAndConsole("[ERROR-CTRL] Error destruyendo ClientNotifierAdapter: " + e.getMessage(), true, e);
            }
        }
    }

    private static class MasterConnectionStatus {
        final boolean connected;
        final int workerCount;
        final String proxyInfo;
        MasterConnectionStatus(boolean connected, int workerCount, String proxyInfo) {
            this.connected = connected;
            this.workerCount = workerCount;
            this.proxyInfo = proxyInfo;
        }
    }
}
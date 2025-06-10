// --- Archivo: client/src/main/java/com/example/client/ClientNotifierI.java ---
package com.example.client;

import perfectNumbersApp.ClientNotifier; // Interfaz generada por Slice para notificaciones del maestro
import perfectNumbersApp.Range; // Clase que describe el rango de búsqueda
import com.zeroc.Ice.Current; // Contexto de la llamada Ice
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Implementación del servant ClientNotifier
 * Este objeto lo invoca el Maestro para notificar al cliente sobre la finalización de un trabajo
 */
public class ClientNotifierI implements ClientNotifier {
    // Referencia al controlador de la vista para actualizar la UI
    private ClientViewController viewController;

    // Constructor recibe el controlador de la vista JavaFX
    public ClientNotifierI(ClientViewController controller) {
        this.viewController = controller;
    }

    @Override
    public CompletionStage<Void> notifyJobCompletionAsync(
            Range originalRange,
            long[] perfectNumbers,
            String statusMessage,
            long elapsedTimeMillisMaster, // Tiempo reportado por el maestro
            Current current) {

        // Detener el temporizador en el cliente y obtener tiempo de ejecución
        long clientSideTotalTime = viewController.stopClientTimerAndGetDuration(); // Obtener tiempo del cliente

        // Construir un mensaje con los resultados de la búsqueda
        StringBuilder sb = new StringBuilder();
        sb.append("\n== NOTIFICACIÓN DE TRABAJO COMPLETADO RECIBIDA DEL MAESTRO ==\n");
        sb.append("Rango Original Solicitado: [").append(originalRange.start).append(" - ").append(originalRange.end).append("]\n");
        sb.append("Números Perfectos Encontrados: ").append(Arrays.toString(perfectNumbers)).append("\n");
        sb.append("Mensaje de Estado del Maestro: ").append(statusMessage).append("\n");
        sb.append("Tiempo de Procesamiento (Maestro): ").append(elapsedTimeMillisMaster).append(" ms\n");
        sb.append("Tiempo Total de Ejecución (Cliente): ").append(clientSideTotalTime).append(" ms\n");
        sb.append("============================================================\n");

        // Si el controlador de la UI existe, actualizar vista y escribir tiempos desde ahí
        if (viewController != null) {
            viewController.appendResults(sb.toString()); // Mostrar resultados en la interfaz
            viewController.jobFinished(); // Habilitar botones
            // Guardar tiempos en archivo desde el controlador
            viewController.writeTimesToFile(originalRange, perfectNumbers, statusMessage, elapsedTimeMillisMaster, clientSideTotalTime);
        } else {
            // Si no hay controlador (modo consola o error), imprimir en consola y archivo
            System.out.println(sb.toString());
            try (PrintWriter writer = new PrintWriter(new FileWriter("tiempos_ejecucion.txt", true))) {
                writer.println("--- EJECUCIÓN (SIN UI CONTROLLER): " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " ---");
                writer.println(sb.toString()); // Escribir el mismo detalle
                writer.println("--- FIN EJECUCIÓN (SIN UI CONTROLLER) ---");
                writer.println();
            } catch (IOException e) {
                // Mostrar error si falla la escritura en archivo de logs
                System.err.println("[ClientNotifierI] Error escribiendo tiempos a archivo (sin UI): " + e.getMessage());
            }
        }

        // Devolver un CompletionStage ya completado, ya que no hay más trabajo asíncrono aquí
        return CompletableFuture.completedFuture(null);
    }
}
// --- Archivo: client/src/main/java/com/example/client/ClientNotifierI.java ---
package com.example.client;

import perfectNumbersApp.ClientNotifier;
import perfectNumbersApp.Range;
import com.zeroc.Ice.Current;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Implementación del servant ClientNotifier.
 * Este objeto es invocado por el Maestro para notificar al cliente sobre la finalización de un trabajo.
 */
public class ClientNotifierI implements ClientNotifier {
    // Referencia al controlador de la vista para actualizar la UI
    private ClientViewController viewController;

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

        long clientSideTotalTime = viewController.stopClientTimerAndGetDuration(); // Obtener tiempo del cliente

        // Construir un mensaje con los resultados de la búsqueda de números perfectos
        StringBuilder sb = new StringBuilder();
        sb.append("\n== NOTIFICACIÓN DE TRABAJO COMPLETADO RECIBIDA DEL MAESTRO ==\n");
        sb.append("Rango Original Solicitado: [").append(originalRange.start).append(" - ").append(originalRange.end).append("]\n");
        sb.append("Números Perfectos Encontrados: ").append(Arrays.toString(perfectNumbers)).append("\n");
        sb.append("Mensaje de Estado del Maestro: ").append(statusMessage).append("\n");
        sb.append("Tiempo de Procesamiento (Maestro): ").append(elapsedTimeMillisMaster).append(" ms\n");
        sb.append("Tiempo Total de Ejecución (Cliente): ").append(clientSideTotalTime).append(" ms\n");
        sb.append("============================================================\n");

        // Actualizar UI y registrar en archivo
        if (viewController != null) {
            viewController.appendResults(sb.toString());
            viewController.jobFinished(); // Habilitar botones, etc.
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
                System.err.println("[ClientNotifierI] Error escribiendo tiempos a archivo (sin UI): " + e.getMessage());
            }
        }

        // Devolver un CompletionStage ya completado, ya que no necesitamos más procesamiento asíncrono aquí
        return CompletableFuture.completedFuture(null);
    }
}
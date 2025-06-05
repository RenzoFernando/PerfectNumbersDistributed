package com.example.client;

import perfectNumbersApp.ClientNotifier;
import perfectNumbersApp.Range;
import com.zeroc.Ice.Current;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
            long elapsedTimeMillis,
            Current current) {

        // Construir un mensaje con los resultados de la búsqueda de números perfectos
        StringBuilder sb = new StringBuilder();
        sb.append("\n== Notificación Recibida ==\n");
        sb.append("Rango Original: [").append(originalRange.start).append(" - ").append(originalRange.end).append("]\n");
        sb.append("Números Perfectos Encontrados: ").append(Arrays.toString(perfectNumbers)).append("\n");
        sb.append("Mensaje de Estado: ").append(statusMessage).append("\n");
        sb.append("Tiempo Transcurrido (ms): ").append(elapsedTimeMillis).append("\n");
        sb.append("================================\n");

        if (viewController != null) {
            // Si el controlador existe, agregar resultados a la interfaz y habilitar el botón
            viewController.appendResults(sb.toString());
            viewController.jobFinished();
        } else {
            // Si no hay controlador (modo consola), imprimir en consola
            System.out.println(sb.toString());
        }

        // Devolver un CompletionStage ya completado, ya que no necesitamos más procesamiento
        return CompletableFuture.completedFuture(null);
    }
}

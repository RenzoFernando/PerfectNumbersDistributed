package com.example.client;

import perfectNumbersApp.ClientNotifier;
import perfectNumbersApp.Range;
import com.zeroc.Ice.Current;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ClientNotifierI implements ClientNotifier {

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

        StringBuilder sb = new StringBuilder();
        sb.append("\n== Notificación Recibida ==\n");
        sb.append("Rango Original: [").append(originalRange.start).append(" - ").append(originalRange.end).append("]\n");
        sb.append("Números Perfectos Encontrados: ").append(Arrays.toString(perfectNumbers)).append("\n");
        sb.append("Mensaje de Estado: ").append(statusMessage).append("\n");
        sb.append("Tiempo Transcurrido (ms): ").append(elapsedTimeMillis).append("\n");
        sb.append("================================\n");

        if (viewController != null) {
            viewController.appendResults(sb.toString());
            viewController.jobFinished();
        } else {
            System.out.println(sb.toString());
        }

        return CompletableFuture.completedFuture(null);
    }
}

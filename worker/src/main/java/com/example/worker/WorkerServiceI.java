// --- Archivo: worker/src/main/java/com/example/worker/WorkerServiceI.java ---
package com.example.worker;

import perfectNumbersApp.Range;
import perfectNumbersApp.MasterControllerPrx;
import perfectNumbersApp.WorkerService;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.LocalException; // Para capturar errores de comunicación con el maestro
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Implementación del servant WorkerService.
 */
public class WorkerServiceI implements WorkerService {

    @Override
    public CompletionStage<Void> processSubRangeAsync(
            Range subRangeToProcess,
            MasterControllerPrx masterCallbackProxy,
            String workerJobId, // ID específico para esta tarea, asignado por el Maestro
            Current current) {

        System.out.println("[" + workerJobId + "] Recibido subrango: [" + subRangeToProcess.start + ", " + subRangeToProcess.end + "]");

        return CompletableFuture.runAsync(() -> {
            long calculationStartTime = System.currentTimeMillis();
            List<Long> foundPerfectNumbersList = WorkerUtils.getPerfectNumbersInRange(
                    subRangeToProcess.start, subRangeToProcess.end);
            long calculationEndTime = System.currentTimeMillis();
            long workerProcessingTimeMillis = calculationEndTime - calculationStartTime;

            long[] perfectNumbersArray = foundPerfectNumbersList.stream().mapToLong(l -> l).toArray();

            System.out.println("[" + workerJobId + "] Números encontrados: " + Arrays.toString(perfectNumbersArray) +
                    ". Tiempo de cálculo ESTE SUBRANGO: " + workerProcessingTimeMillis + " ms.");

            if (masterCallbackProxy != null) {
                try {
                    System.out.println("[" + workerJobId + "] Enviando " + perfectNumbersArray.length + " resultado(s) al MasterController...");
                    masterCallbackProxy.submitWorkerResultsAsync(workerJobId, subRangeToProcess, perfectNumbersArray, workerProcessingTimeMillis);
                    System.out.println("[" + workerJobId + "] Resultados enviados al MasterController.");
                } catch (LocalException e) {
                    System.err.println("[" + workerJobId + "] ERROR al enviar resultados al MasterController: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    // e.printStackTrace(); // Descomentar para ver traza completa
                    // En un sistema real, aquí podría haber lógica de reintento o notificación de fallo.
                } catch (Exception e) {
                    System.err.println("[" + workerJobId + "] ERROR INESPERADO al enviar resultados al MasterController: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("[" + workerJobId + "] ERROR: MasterCallbackProxy es nulo. No se pueden enviar resultados.");
            }
        });
    }
}

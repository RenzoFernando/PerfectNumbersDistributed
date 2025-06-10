// --- Archivo: worker/src/main/java/com/example/worker/WorkerServiceI.java ---
package com.example.worker;

import perfectNumbersApp.Range; // Rango a procesar definido por el cliente/maestro
import perfectNumbersApp.MasterControllerPrx; // Proxy para notificar resultados al Maestro
import perfectNumbersApp.WorkerService; // Interfaz de Slice para este servicio
import com.zeroc.Ice.Current; // Contexto de la llamada Ice
import com.zeroc.Ice.LocalException; // Captura errores de comunicación con el maestro
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Implementación del servant WorkerService.
 * Recibe un subrango de números, calcula los perfectos y notifica al Maestro.
 */
public class WorkerServiceI implements WorkerService {

    /**
     * Método asíncrono llamado por el Maestro para procesar un rango.
     * @param subRangeToProcess rango de valores a revisar
     * @param masterCallbackProxy proxy para notificar los resultados al Maestro
     * @param workerJobId ID único de esta tarea asignado por el Maestro
     * @param current contexto de Ice (omitido en la lógica)
     * @return CompletionStage completado cuando termine de procesar y notificar
     */
    @Override
    public CompletionStage<Void> processSubRangeAsync(
            Range subRangeToProcess,
            MasterControllerPrx masterCallbackProxy,
            String workerJobId, // ID específico para esta tarea, asignado por el Maestro
            Current current) {

        // Mostrar en consola el subrango que se va a procesar
        System.out.println("[" + workerJobId + "] Recibido subrango: [" + subRangeToProcess.start + ", " + subRangeToProcess.end + "]");

        // Ejecutar el cálculo en un hilo separado para no bloquear el servidor Ice
        return CompletableFuture.runAsync(() -> {
            // Medir tiempo de cálculo local
            long calculationStartTime = System.currentTimeMillis();
            // Obtener lista de números perfectos en el rango indicado
            List<Long> foundPerfectNumbersList = WorkerUtils.getPerfectNumbersInRange(
                    subRangeToProcess.start, subRangeToProcess.end);
            long calculationEndTime = System.currentTimeMillis();
            long workerProcessingTimeMillis = calculationEndTime - calculationStartTime;

            // Convertir la lista a un array primitivo para enviar a través de Ice
            long[] perfectNumbersArray = foundPerfectNumbersList.stream().mapToLong(l -> l).toArray();

            // Mostrar resultados y tiempo de cálculo en consola
            System.out.println("[" + workerJobId + "] Números encontrados: " + Arrays.toString(perfectNumbersArray) +
                    ". Tiempo de cálculo ESTE SUBRANGO: " + workerProcessingTimeMillis + " ms.");

            // Si el proxy al Maestro es válido, enviar los resultados
            if (masterCallbackProxy != null) {
                try {
                    System.out.println("[" + workerJobId + "] Enviando " + perfectNumbersArray.length + " resultado(s) al MasterController...");
                    masterCallbackProxy.submitWorkerResultsAsync(workerJobId, subRangeToProcess, perfectNumbersArray, workerProcessingTimeMillis);
                    System.out.println("[" + workerJobId + "] Resultados enviados al MasterController.");
                } catch (LocalException e) {
                    // Error de comunicación local
                    System.err.println("[" + workerJobId + "] ERROR al enviar resultados al MasterController: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    // e.printStackTrace(); // Descomentar para ver traza completa
                } catch (Exception e) {
                    // Cualquier otro error inesperado
                    System.err.println("[" + workerJobId + "] ERROR INESPERADO al enviar resultados al MasterController: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // Proxy nulo: no hay a quién notificar
                System.err.println("[" + workerJobId + "] ERROR: MasterCallbackProxy es nulo. No se pueden enviar resultados.");
            }
        });
    }
}

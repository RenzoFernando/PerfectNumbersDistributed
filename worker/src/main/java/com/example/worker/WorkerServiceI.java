package com.example.worker;

import perfectNumbersApp.Range; // Clase generada por ICE que representa un rango
import perfectNumbersApp.MasterControllerPrx; // Proxy para el controlador del Maestro
import perfectNumbersApp.WorkerService; // Interfaz generada por ICE para el servicio del worker

import com.zeroc.Ice.Current;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class WorkerServiceI implements WorkerService {

    /**
     * Método remoto que el Maestro invoca para procesar un subrango.
     * @param subRangeToProcess Rango que este worker debe procesar.
     * @param masterCallbackProxy Proxy para enviar los resultados de vuelta al MasterController.
     * @param workerId Identificador único de este trabajo (asignado por el Maestro).
     * @param current Contexto ICE (no se usa directamente aquí).
     * @return CompletionStage<Void> para indicar que la llamada es asíncrona.
     */
    @Override
    public CompletionStage<Void> processSubRangeAsync(
            perfectNumbersApp.Range subRangeToProcess, // Usar el tipo correcto
            MasterControllerPrx masterCallbackProxy,
            String workerId,
            Current current) {

        // Imprimir en consola qué subrango se va a procesar
        System.out.println("[WORKER " + workerId + "] Recibido subrango para procesar: [" +
                subRangeToProcess.start + ", " + subRangeToProcess.end + "]");

        // Ejecutar la tarea en un hilo separado para no bloquear Ice
        return CompletableFuture.runAsync(() -> {
            // Obtener la lista de números perfectos en este subrango
            List<Long> foundPerfectNumbersList = WorkerUtils.getPerfectNumbersInRange(
                    subRangeToProcess.start, subRangeToProcess.end);

            // Convertir la lista de Long a un arreglo primitivo long[]
            long[] perfectNumbersArray = new long[foundPerfectNumbersList.size()];
            for (int i = 0; i < foundPerfectNumbersList.size(); i++) {
                perfectNumbersArray[i] = foundPerfectNumbersList.get(i);
            }

            // Mostrar en consola los números perfectos encontrados
            System.out.println("[WORKER " + workerId + "] Números perfectos encontrados en el subrango: " +
                    Arrays.toString(perfectNumbersArray));

            // Enviar resultados de vuelta al MasterController si el proxy es válido
            if (masterCallbackProxy != null) {
                try {
                    System.out.println("[WORKER " + workerId + "] Enviando resultados al MasterController...");
                    masterCallbackProxy.submitWorkerResultsAsync(workerId, subRangeToProcess, perfectNumbersArray);
                    System.out.println("[WORKER " + workerId + "] Resultados enviados.");
                } catch (Exception e) {
                    // Si falla el envío, mostrar error en consola
                    System.err.println("[WORKER " + workerId + "] Error al enviar resultados al MasterController: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // Si el proxy es nulo, no podemos notificar al Maestro
                System.err.println("[WORKER " + workerId + "] MasterCallbackProxy es nulo. No se pueden enviar resultados.");
            }
        });
    }
}

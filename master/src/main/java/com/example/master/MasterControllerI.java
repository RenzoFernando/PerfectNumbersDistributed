package com.example.master;

import perfectNumbersApp.Range; // Clase generada por ICE que representa un rango con 'start' y 'end'
import perfectNumbersApp.MasterController; // Interfaz generada por ICE

import com.zeroc.Ice.Current;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MasterControllerI implements MasterController {

    // Mapa que guarda los resultados parciales de cada worker (clave: workerId, valor: arreglo de números perfectos)
    private final Map<String, long[]> partialResults = new ConcurrentHashMap<>();

    // Latch que se usa para esperar a que todos los workers terminen o fallen
    private CountDownLatch jobCompletionLatch; // Mantener privado

    /**
     * Prepara el controlador para un nuevo trabajo.
     * @param numberOfWorkers Cantidad de workers a los que se asignó trabajo.
     */
    public void resetForNewJob(int numberOfWorkers) {
        partialResults.clear(); // Limpiar resultados previos
        if (numberOfWorkers > 0) {
            // Inicializar el latch con el número de workers que van a responder
            jobCompletionLatch = new CountDownLatch(numberOfWorkers);
        } else {
            // Si no hay workers, el latch ya está a 0 para no esperar
            jobCompletionLatch = new CountDownLatch(0); // Latch ya contado si no hay workers
        }
        System.out.println("[MASTER_CONTROLLER] Reiniciado para nuevo trabajo. Esperando " + (numberOfWorkers > 0 ? numberOfWorkers : 0) + " respuestas.");
    }

    /**
     * Si un worker falla o no recibe tarea, descontar del latch para no quedar esperando indefinidamente.
     */
    public void handleWorkerFailure() {
        if (jobCompletionLatch != null && jobCompletionLatch.getCount() > 0) {
            jobCompletionLatch.countDown();
            System.out.println("[MASTER_CONTROLLER] Falla de worker manejada/worker no usado. Latch ahora en: " + jobCompletionLatch.getCount());
        }
    }

    /**
     * Permite consultar cuántas respuestas faltan según el latch.
     * @return Cantidad de contadores todavía activos en el latch.
     */
    public long getJobCompletionLatchCount() {
        return (jobCompletionLatch != null) ? jobCompletionLatch.getCount() : 0;
    }

    /**
     * Método que los workers invocan para enviar sus resultados al Maestro.
     * @param workerId Identificador único del worker.
     * @param processedSubRange Subrango que procesó este worker.
     * @param perfectNumbersFound Arreglo de números perfectos encontrados en ese subrango.
     * @param current Contexto ICE (no se usa directamente aquí).
     * @return CompletionStage para la llamada asíncrona (completado instantáneo).
     */
    @Override
    public CompletionStage<Void> submitWorkerResultsAsync(
            String workerId,
            perfectNumbersApp.Range processedSubRange, // Usar el tipo correcto
            long[] perfectNumbersFound, // NameList (sequence<long>) se mapea a long[]
            Current current) {

        // Imprimir en consola qué subrango procesó y qué resultados obtuvo
        System.out.println("[MASTER_CONTROLLER] Resultados recibidos del worker: " + workerId +
                " para el subrango [" + processedSubRange.start + ", " + processedSubRange.end + "]. " +
                "Números perfectos encontrados: " + Arrays.toString(perfectNumbersFound));

        // Guardar estos resultados en el mapa, usando como llave el workerId
        partialResults.put(workerId, perfectNumbersFound);

        // Descontar uno del latch si aún hay contadores activos
        if (jobCompletionLatch != null && jobCompletionLatch.getCount() > 0) {
            jobCompletionLatch.countDown();
            System.out.println("[MASTER_CONTROLLER] Respuesta contada del worker " + workerId + ". Latch ahora en: " + jobCompletionLatch.getCount());
        } else {
            // En caso de recibir más respuestas de las esperadas o latch nulo
            System.err.println("[MASTER_CONTROLLER] Latch nulo o ya en cero al recibir resultados de " + workerId + ". Latch: " + getJobCompletionLatchCount());
        }

        // Devolver un CompletionStage ya completado porque no necesitamos hacer más trabajo luego
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Esperar hasta que todos los workers hayan respondido o hasta que pase el timeout.
     * @param timeoutMillis Milisegundos máximos para esperar.
     * @return true si todos los workers respondieron antes del timeout, false si hubo timeout o se interrumpió.
     */
    public boolean awaitJobCompletion(long timeoutMillis) {
        if (jobCompletionLatch == null) {
            System.err.println("[MASTER_CONTROLLER] awaitJobCompletion llamado pero el latch es nulo.");
            return true; // Consideramos que ya está "completado"
        }
        if (jobCompletionLatch.getCount() == 0) {
            // Si ya estaba en cero, no esperar
            System.out.println("[MASTER_CONTROLLER] El trabajo ya está completado (latch en cero) al inicio de awaitJobCompletion.");
            return true;
        }
        try {
            System.out.println("[MASTER_CONTROLLER] Esperando la finalización de " + jobCompletionLatch.getCount() + " workers...");
            // Esperar hasta que el latch llegue a 0 o se alcance el timeout
            boolean completed = jobCompletionLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                System.err.println("[MASTER_CONTROLLER] Timeout esperando a los workers. Faltaron " + jobCompletionLatch.getCount() + " respuestas.");
            } else {
                System.out.println("[MASTER_CONTROLLER] Todos los workers han respondido o timeout/fallas manejadas.");
            }
            return completed;
        } catch (InterruptedException e) {
            System.err.println("[MASTER_CONTROLLER] Hilo interrumpido mientras esperaba la finalización del trabajo.");
            Thread.currentThread().interrupt(); // Restaurar la interrupción
            return false;
        }
    }

    /**
     * Combina todos los números perfectos encontrados por cada worker en un solo arreglo ordenado.
     * @return Arreglo de todos los números perfectos, ordenados de menor a mayor.
     */
    public long[] getAllFoundPerfectNumbers() {
        // Crear una lista para juntar todos los valores de cada arreglo
        List<Long> allPerfectsList = new ArrayList<>();
        for (long[] results : partialResults.values()) {
            for (long p : results) {
                allPerfectsList.add(p);
            }
        }

        // Ordenar la lista de números perfectos
        Collections.sort(allPerfectsList);

        // Convertir la lista ordenada a un arreglo de tipo primitivo long[]
        long[] finalResult = new long[allPerfectsList.size()];
        for (int i = 0; i < allPerfectsList.size(); i++) {
            finalResult[i] = allPerfectsList.get(i);
        }
        System.out.println("[MASTER_CONTROLLER] Resultados consolidados: " + Arrays.toString(finalResult));
        return finalResult;
    }
}

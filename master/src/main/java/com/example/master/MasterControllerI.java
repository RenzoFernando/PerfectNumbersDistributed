package com.example.master;

import perfectNumbersApp.Range; // Correcto
import perfectNumbersApp.MasterController; // La interfaz generada

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

    private final Map<String, long[]> partialResults = new ConcurrentHashMap<>();
    private CountDownLatch jobCompletionLatch; // Mantener privado

    public void resetForNewJob(int numberOfWorkers) {
        partialResults.clear();
        if (numberOfWorkers > 0) {
            jobCompletionLatch = new CountDownLatch(numberOfWorkers);
        } else {
            jobCompletionLatch = new CountDownLatch(0); // Latch ya contado si no hay workers
        }
        System.out.println("[MASTER_CONTROLLER] Reiniciado para nuevo trabajo. Esperando " + (numberOfWorkers > 0 ? numberOfWorkers : 0) + " respuestas.");
    }

    public void handleWorkerFailure() {
        if (jobCompletionLatch != null && jobCompletionLatch.getCount() > 0) {
            jobCompletionLatch.countDown();
            System.out.println("[MASTER_CONTROLLER] Falla de worker manejada/worker no usado. Latch ahora en: " + jobCompletionLatch.getCount());
        }
    }

    // Método público para que MasterServiceI pueda obtener el conteo del latch
    public long getJobCompletionLatchCount() {
        return (jobCompletionLatch != null) ? jobCompletionLatch.getCount() : 0;
    }

    @Override
    public CompletionStage<Void> submitWorkerResultsAsync(
            String workerId,
            perfectNumbersApp.Range processedSubRange, // Usar el tipo correcto
            long[] perfectNumbersFound, // NameList (sequence<long>) se mapea a long[]
            Current current) {

        System.out.println("[MASTER_CONTROLLER] Resultados recibidos del worker: " + workerId +
                " para el subrango [" + processedSubRange.start + ", " + processedSubRange.end + "]. " +
                "Números perfectos encontrados: " + Arrays.toString(perfectNumbersFound));
        partialResults.put(workerId, perfectNumbersFound);

        if (jobCompletionLatch != null && jobCompletionLatch.getCount() > 0) {
            jobCompletionLatch.countDown();
            System.out.println("[MASTER_CONTROLLER] Respuesta contada del worker " + workerId + ". Latch ahora en: " + jobCompletionLatch.getCount());
        } else {
            System.err.println("[MASTER_CONTROLLER] Latch nulo o ya en cero al recibir resultados de " + workerId + ". Latch: " + getJobCompletionLatchCount());
        }
        return CompletableFuture.completedFuture(null);
    }

    public boolean awaitJobCompletion(long timeoutMillis) {
        if (jobCompletionLatch == null) {
            System.err.println("[MASTER_CONTROLLER] awaitJobCompletion llamado pero el latch es nulo.");
            return true;
        }
        if (jobCompletionLatch.getCount() == 0) {
            System.out.println("[MASTER_CONTROLLER] El trabajo ya está completado (latch en cero) al inicio de awaitJobCompletion.");
            return true;
        }
        try {
            System.out.println("[MASTER_CONTROLLER] Esperando la finalización de " + jobCompletionLatch.getCount() + " workers...");
            boolean completed = jobCompletionLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                System.err.println("[MASTER_CONTROLLER] Timeout esperando a los workers. Faltaron " + jobCompletionLatch.getCount() + " respuestas.");
            } else {
                System.out.println("[MASTER_CONTROLLER] Todos los workers han respondido o timeout/fallas manejadas.");
            }
            return completed;
        } catch (InterruptedException e) {
            System.err.println("[MASTER_CONTROLLER] Hilo interrumpido mientras esperaba la finalización del trabajo.");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public long[] getAllFoundPerfectNumbers() {
        List<Long> allPerfectsList = new ArrayList<>();
        for (long[] results : partialResults.values()) {
            for (long p : results) {
                allPerfectsList.add(p);
            }
        }
        Collections.sort(allPerfectsList);

        long[] finalResult = new long[allPerfectsList.size()];
        for (int i = 0; i < allPerfectsList.size(); i++) {
            finalResult[i] = allPerfectsList.get(i);
        }
        System.out.println("[MASTER_CONTROLLER] Resultados consolidados: " + Arrays.toString(finalResult));
        return finalResult;
    }
}

// --- Archivo: master/src/main/java/com/example/master/MasterControllerI.java ---
package com.example.master;

import perfectNumbersApp.Range;
import perfectNumbersApp.MasterController;
import com.zeroc.Ice.Current;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implementación del servant MasterController.
 * Este objeto es invocado por los Workers para reportar los resultados de los subrangos
 * que han procesado.
 */
public class MasterControllerI implements MasterController {
    private final Map<String, WorkerResult> partialResults = new ConcurrentHashMap<>();
    private CountDownLatch jobCompletionLatch;
    private String currentJobLogId; // Para agrupar logs de un trabajo en el archivo

    private static class WorkerResult {
        final long[] numbers;
        final long processingTime;
        WorkerResult(long[] numbers, long processingTime) {
            this.numbers = numbers;
            this.processingTime = processingTime;
        }
    }

    public void resetForNewJob(int numberOfParticipatingWorkers, String jobLogId) {
        partialResults.clear();
        this.currentJobLogId = jobLogId;
        if (numberOfParticipatingWorkers > 0) {
            jobCompletionLatch = new CountDownLatch(numberOfParticipatingWorkers);
        } else {
            jobCompletionLatch = new CountDownLatch(0);
        }
        System.out.println("[MASTER_CONTROLLER] ("+jobLogId+") Estado reiniciado. Esperando " + numberOfParticipatingWorkers + " respuesta(s).");
    }

    public void handleWorkerFailureOrNoTask(String workerId) {
        if (jobCompletionLatch != null && jobCompletionLatch.getCount() > 0) {
            jobCompletionLatch.countDown();
            System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Falla/No-Tarea para worker " + workerId + ". Latch ahora en: " + jobCompletionLatch.getCount());
            // Registrar esta "falla" o no asignación en el log de tiempos
            try (PrintWriter writer = new PrintWriter(new FileWriter("tiempos_ejecucion.txt", true))) {
                writer.println("Job ID: " + currentJobLogId + " - Worker ID: " + workerId + " - Tiempo Procesamiento: N/A (Falla o no asignado)");
            } catch (IOException e) {
                System.err.println("[MASTER_CONTROLLER] Error escribiendo log de falla de worker: " + e.getMessage());
            }
        }
    }

    public long getJobCompletionLatchCount() {
        return (jobCompletionLatch != null) ? jobCompletionLatch.getCount() : 0;
    }

    @Override
    public CompletionStage<Void> submitWorkerResultsAsync(
            String workerId,
            Range processedSubRange,
            long[] perfectNumbersFound,
            long workerProcessingTimeMillis, // Nuevo parámetro
            Current current) {

        System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Resultados de worker: " + workerId +
                " para subrango [" + processedSubRange.start + ", " + processedSubRange.end + "]. " +
                "Encontrados: " + Arrays.toString(perfectNumbersFound) +
                ". Tiempo del worker: " + workerProcessingTimeMillis + " ms.");

        partialResults.put(workerId, new WorkerResult(perfectNumbersFound, workerProcessingTimeMillis));

        // Registrar tiempo del worker en el archivo
        try (PrintWriter writer = new PrintWriter(new FileWriter("tiempos_ejecucion.txt", true))) {
            // Asumiendo que currentJobLogId se establece al inicio de un nuevo trabajo en MasterServiceI
            writer.println("Job ID: " + currentJobLogId +
                    " - Worker ID: " + workerId +
                    " - Subrango: [" + processedSubRange.start + "-" + processedSubRange.end + "]" +
                    " - Tiempo Procesamiento Worker: " + workerProcessingTimeMillis + " ms" +
                    " - Perfectos Encontrados (Worker): " + Arrays.toString(perfectNumbersFound));
        } catch (IOException e) {
            System.err.println("[MASTER_CONTROLLER] Error escribiendo tiempo de worker a archivo: " + e.getMessage());
        }


        if (jobCompletionLatch != null && jobCompletionLatch.getCount() > 0) {
            jobCompletionLatch.countDown();
            System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Respuesta contada de " + workerId + ". Latch: " + jobCompletionLatch.getCount());
        } else {
            System.err.println("[MASTER_CONTROLLER] ("+currentJobLogId+") ADVERTENCIA: Latch nulo o en cero al recibir de " + workerId +
                    ". Latch: " + (jobCompletionLatch != null ? jobCompletionLatch.getCount() : "nulo"));
        }
        return CompletableFuture.completedFuture(null);
    }

    public boolean awaitJobCompletion(long timeoutMillis) {
        if (jobCompletionLatch == null) {
            System.err.println("[MASTER_CONTROLLER] ("+currentJobLogId+") awaitJobCompletion: Latch nulo. Asumiendo no había trabajo.");
            return true;
        }
        if (jobCompletionLatch.getCount() == 0) {
            System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Trabajo ya completado (latch en cero) al inicio de await.");
            return true;
        }
        try {
            System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Esperando finalización de " + jobCompletionLatch.getCount() +
                    " worker(s) restantes (timeout: " + timeoutMillis + "ms)...");
            boolean completed = jobCompletionLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                System.err.println("[MASTER_CONTROLLER] ("+currentJobLogId+") TIMEOUT esperando workers. Faltaron " +
                        jobCompletionLatch.getCount() + " después de " + timeoutMillis + "ms.");
            } else {
                System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Todos los workers asignados respondieron o fallas manejadas.");
            }
            return completed;
        } catch (InterruptedException e) {
            System.err.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Hilo interrumpido esperando workers.");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public long[] getAllFoundPerfectNumbers() {
        List<Long> allPerfectsList = new ArrayList<>();
        for (WorkerResult result : partialResults.values()) {
            for (long p : result.numbers) {
                allPerfectsList.add(p);
            }
        }
        Collections.sort(allPerfectsList);
        long[] finalResult = allPerfectsList.stream().mapToLong(l -> l).toArray();
        System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Resultados parciales consolidados. Total: " + finalResult.length +
                ". Números: " + Arrays.toString(finalResult));
        return finalResult;
    }
}
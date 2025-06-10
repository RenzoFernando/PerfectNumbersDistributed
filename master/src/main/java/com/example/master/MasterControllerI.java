// --- Archivo: master/src/main/java/com/example/master/MasterControllerI.java ---
package com.example.master;

import perfectNumbersApp.Range; // Rango procesado por cada worker
import perfectNumbersApp.MasterController; // Interfaz generada por Slice para el controlador del Maestro
import com.zeroc.Ice.Current; // Contexto de la llamada Ice
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
 * Servant que implementa MasterController
 * Recibe resultados de cada worker y coordina la agregación
 */
public class MasterControllerI implements MasterController {
    // Almacena resultados de cada worker: números perfectos y tiempo de proceso
    private final Map<String, WorkerResult> partialResults = new ConcurrentHashMap<>();
    private CountDownLatch jobCompletionLatch; // Para esperar a todos los workers
    private String currentJobLogId; // Identificador para agrupar logs de un mismo trabajo

    // Clase interna para guardar datos de un worker
    private static class WorkerResult {
        final long[] numbers; // Números perfectos encontrados por el worker
        final long processingTime; // Tiempo que tardó el worker
        WorkerResult(long[] numbers, long processingTime) {
            this.numbers = numbers;
            this.processingTime = processingTime;
        }
    }

    /**
     * Prepara el controlador para un nuevo trabajo.
     * Limpia resultados previos y crea un CountDownLatch con el número de workers.
     * @param numberOfParticipatingWorkers número de workers esperados
     * @param jobLogId identificador único para este trabajo
     */
    public void resetForNewJob(int numberOfParticipatingWorkers, String jobLogId) {
        partialResults.clear();
        this.currentJobLogId = jobLogId;
        // Crear latch que espera a cada worker o falla
        if (numberOfParticipatingWorkers > 0) {
            jobCompletionLatch = new CountDownLatch(numberOfParticipatingWorkers);
        } else {
            jobCompletionLatch = new CountDownLatch(0);
        }
        System.out.println("[MASTER_CONTROLLER] ("+jobLogId+") Estado reiniciado. Esperando " + numberOfParticipatingWorkers + " respuesta(s).");
    }

    /**
     * Maneja casos donde un worker falla o no recibe tarea.
     * Decrementa el latch y escribe un log indicando la falla.
     */
    public void handleWorkerFailureOrNoTask(String workerId) {
        if (jobCompletionLatch != null && jobCompletionLatch.getCount() > 0) {
            jobCompletionLatch.countDown();
            System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Falla/No-Tarea para worker " + workerId + ". Latch ahora en: " + jobCompletionLatch.getCount());
            // Loguear la ausencia de resultados del worker
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

    /**
     * Método asíncrono llamado por cada worker con sus resultados.
     * Guarda los datos, decrementa el latch y escribe un log.
     */
    @Override
    public CompletionStage<Void> submitWorkerResultsAsync(
            String workerId,
            Range processedSubRange,
            long[] perfectNumbersFound,
            long workerProcessingTimeMillis, // Nuevo parámetro
            Current current) {

        // Mostrar en consola resumen del resultado del worker
        System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Resultados de worker: " + workerId +
                " para subrango [" + processedSubRange.start + ", " + processedSubRange.end + "]. " +
                "Encontrados: " + Arrays.toString(perfectNumbersFound) +
                ". Tiempo del worker: " + workerProcessingTimeMillis + " ms.");

        // Guardar resultado en el map
        partialResults.put(workerId, new WorkerResult(perfectNumbersFound, workerProcessingTimeMillis));

        // Registrar tiempo del worker en archivo de log
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

        // Contar respuesta en el latch
        if (jobCompletionLatch != null && jobCompletionLatch.getCount() > 0) {
            jobCompletionLatch.countDown();
            System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Respuesta contada de " + workerId + ". Latch: " + jobCompletionLatch.getCount());
        } else {
            System.err.println("[MASTER_CONTROLLER] ("+currentJobLogId+") ADVERTENCIA: Latch nulo o en cero al recibir de " + workerId +
                    ". Latch: " + (jobCompletionLatch != null ? jobCompletionLatch.getCount() : "nulo"));
        }
        // Retornar CompletionStage completado
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Espera hasta que todos los workers hayan respondido o hasta el timeout.
     * Devuelve true si completaron todos a tiempo, false si hubo timeout o interrupción.
     */
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

    /**
     * Consolida todos los números perfectos encontrados por los workers,
     * los ordena y retorna un array con el resultado final.
     */
    public long[] getAllFoundPerfectNumbers() {
        List<Long> allPerfectsList = new ArrayList<>();
        // Recorrer resultados de cada worker
        for (WorkerResult result : partialResults.values()) {
            for (long p : result.numbers) {
                allPerfectsList.add(p);
            }
        }
        // Ordenar la lista antes de convertir
        Collections.sort(allPerfectsList);
        long[] finalResult = allPerfectsList.stream().mapToLong(l -> l).toArray();
        System.out.println("[MASTER_CONTROLLER] ("+currentJobLogId+") Resultados parciales consolidados. Total: " + finalResult.length +
                ". Números: " + Arrays.toString(finalResult));
        return finalResult;
    }
}
// --- Archivo: master/src/main/java/com/example/master/MasterServiceI.java ---
package com.example.master;

import com.zeroc.Ice.Exception;
import perfectNumbersApp.*; // Clases generadas por ICE
import com.zeroc.Ice.*; // Clases base de ICE
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays; // Para Arrays.toString
import java.util.Collections; // Para Collections.emptyList
import java.util.Date;
import java.util.List; // Interfaz para listas
import java.util.UUID; // Para generar IDs únicos
import java.util.concurrent.CompletableFuture; // Para programación asíncrona
import java.util.concurrent.CopyOnWriteArrayList; // Lista segura para concurrencia

/**
 * Implementación del servicio Maestro (MasterService).
 */
public class MasterServiceI implements MasterService {
    private final ObjectAdapter adapter;
    private final List<WorkerServicePrx> registeredWorkers = new CopyOnWriteArrayList<>();
    private final MasterControllerPrx masterControllerProxy;
    private final MasterControllerI masterControllerServant;
    private static final int MAX_WORKERS_TO_USE_FOR_A_JOB = 10; // Límite de workers por tarea
    private final String masterLogId = "Master-" + UUID.randomUUID().toString().substring(0,4);


    public MasterServiceI(ObjectAdapter adapter, Communicator communicator) {
        this.adapter = adapter;
        this.masterControllerServant = new MasterControllerI();
        String controllerIdentity = "MasterController-" + UUID.randomUUID().toString();
        this.masterControllerProxy = MasterControllerPrx.uncheckedCast(
                adapter.add(this.masterControllerServant, Util.stringToIdentity(controllerIdentity))
        );
        System.out.println("["+masterLogId+"] MasterController interno creado: " + controllerIdentity);
    }

    @Override
    public void registerWorker(WorkerServicePrx workerProxy, Current current) {
        if (workerProxy == null) {
            System.err.println("["+masterLogId+"] Intento de registrar worker con proxy nulo. Ignorando.");
            return;
        }
        Identity workerIdentity = workerProxy.ice_getIdentity();
        String workerStringRep = getProxyStringRepresentation(workerProxy);

        boolean alreadyRegistered = false;
        for (WorkerServicePrx existingWorker : registeredWorkers) {
            if (existingWorker.ice_getIdentity().equals(workerIdentity)) {
                alreadyRegistered = true;
                break;
            }
        }

        if (!alreadyRegistered) {
            try {
                workerProxy.ice_ping(); // Ping para asegurar que está activo
                registeredWorkers.add(workerProxy);
                System.out.println("["+masterLogId+"] Worker '" + workerIdentity.name + "' (" + workerStringRep + ") registrado (ping OK). Total: " + registeredWorkers.size());
            } catch (Exception e) {
                System.err.println("["+masterLogId+"] Error ping worker '" + workerIdentity.name + "' (" + workerStringRep + "): " + e.getClass().getSimpleName() + ". No añadido.");
            }
        } else {
            System.out.println("["+masterLogId+"] Worker '" + workerIdentity.name + "' (" + workerStringRep + ") ya estaba registrado.");
        }
    }

    @Override
    public int getActiveWorkerCount(Current current) {
        System.out.println("["+masterLogId+"] Solicitud `getActiveWorkerCount` del cliente.");
        List<WorkerServicePrx> workersToRemove = new ArrayList<>();
        for (WorkerServicePrx worker : registeredWorkers) {
            try {
                worker.ice_ping();
            } catch (LocalException le) {
                System.err.println("["+masterLogId+"] getActiveWorkerCount: Worker " + getProxyStringRepresentation(worker) + " no respondió (Excepción Local: " + le.getClass().getSimpleName() + "). Removiendo.");
                workersToRemove.add(worker);
            } catch (Exception e) {
                System.err.println("["+masterLogId+"] getActiveWorkerCount: Excepción inesperada ping worker " + getProxyStringRepresentation(worker) + ": " + e.getMessage() + ". Removiendo.");
                workersToRemove.add(worker);
            }
        }
        if (!workersToRemove.isEmpty()) {
            registeredWorkers.removeAll(workersToRemove);
            System.out.println("["+masterLogId+"] getActiveWorkerCount: Removidos " + workersToRemove.size() + " workers inactivos. Registrados ahora: " + registeredWorkers.size());
        }
        System.out.println("["+masterLogId+"] Workers activos reportados: " + registeredWorkers.size());
        return registeredWorkers.size();
    }

    @Override
    public void findPerfectNumbersInRange(
            Range jobRange,
            ClientNotifierPrx clientNotifierProxy,
            int numWorkersRequestedByClient,
            Current current) {

        String jobLogId = "Job-" + UUID.randomUUID().toString().substring(0, 5);
        System.out.println("["+masterLogId+"] ("+jobLogId+") Solicitud: Rango [" + jobRange.start + ", " + jobRange.end + "], Workers Solicitados: " + numWorkersRequestedByClient);

        if (clientNotifierProxy == null) {
            System.err.println("["+masterLogId+"] ("+jobLogId+") ERROR CRÍTICO: ClientNotifierPrx es nulo.");
            return;
        }

        List<WorkerServicePrx> workersForThisJob = selectWorkersForTask(numWorkersRequestedByClient, jobLogId);

        if (workersForThisJob.isEmpty()) {
            System.out.println("["+masterLogId+"] ("+jobLogId+") No hay workers activos. Notificando al cliente.");
            try {
                clientNotifierProxy.notifyJobCompletionAsync(jobRange, new long[0], "No hay workers activos disponibles.", 0L);
            } catch (Exception e) {
                System.err.println("["+masterLogId+"] ("+jobLogId+") Error notificando al cliente (no workers): " + e.getMessage());
            }
            return;
        }

        long jobStartTimeOnMaster = System.currentTimeMillis();
        masterControllerServant.resetForNewJob(workersForThisJob.size(), jobLogId); // Pasar jobLogId

        long totalNumbersToProcess = jobRange.end - jobRange.start + 1;
        if (totalNumbersToProcess <= 0) {
            System.err.println("["+masterLogId+"] ("+jobLogId+") Rango inválido. Notificando cliente.");
            try {
                clientNotifierProxy.notifyJobCompletionAsync(jobRange, new long[0], "Rango inválido.", 0L);
            } catch (Exception e) {
                System.err.println("["+masterLogId+"] ("+jobLogId+") Error notificando al cliente (rango inválido): " + e.getMessage());
            }
            masterControllerServant.resetForNewJob(0, jobLogId);
            return;
        }

        long numbersPerWorker = Math.max(1, (totalNumbersToProcess + workersForThisJob.size() - 1) / workersForThisJob.size());
        long currentSubRangeStart = jobRange.start;
        int actualWorkersDispatched = 0;

        System.out.println("["+masterLogId+"] ("+jobLogId+") Distribuyendo. Workers seleccionados: " + workersForThisJob.size() + ". Números/worker aprox: " + numbersPerWorker);

        for (int i = 0; i < workersForThisJob.size(); i++) {
            if (currentSubRangeStart > jobRange.end) {
                System.out.println("["+masterLogId+"] ("+jobLogId+") Rango completo asignado. " + (workersForThisJob.size() - i) + " worker(s) no recibirán tarea.");
                for (int j = i; j < workersForThisJob.size(); j++) {
                    masterControllerServant.handleWorkerFailureOrNoTask(getProxyStringRepresentation(workersForThisJob.get(j)));
                }
                break;
            }

            WorkerServicePrx worker = workersForThisJob.get(i);
            long currentSubRangeEnd = Math.min(jobRange.end, currentSubRangeStart + numbersPerWorker - 1);

            if (currentSubRangeStart > currentSubRangeEnd) {
                System.out.println("["+masterLogId+"] ("+jobLogId+") Subrango para worker " + getProxyStringRepresentation(worker) + " sería inválido. Ajustando latch.");
                masterControllerServant.handleWorkerFailureOrNoTask(getProxyStringRepresentation(worker));
                continue;
            }

            Range subRange = new Range(currentSubRangeStart, currentSubRangeEnd);
            String workerJobId = jobLogId + "-W" + (i+1) + "-" + worker.ice_getIdentity().name.substring(0, Math.min(8, worker.ice_getIdentity().name.length()));
            String workerStringRep = getProxyStringRepresentation(worker);
            System.out.println("["+masterLogId+"] ("+jobLogId+") Enviando [" + subRange.start + ", " + subRange.end + "] a " + workerStringRep + " (ID Tarea Worker: " + workerJobId + ")");

            try {
                worker.processSubRangeAsync(subRange, this.masterControllerProxy, workerJobId);
                actualWorkersDispatched++;
            } catch (Exception e) {
                System.err.println("["+masterLogId+"] ("+jobLogId+") ERROR al enviar a " + workerStringRep + ": " + e.getMessage());
                masterControllerServant.handleWorkerFailureOrNoTask(getProxyStringRepresentation(worker));
                // Considerar remover el worker si falla el envío, ya que podría estar caído.
                // registeredWorkers.remove(worker); // Cuidado con ConcurrentModificationException si no es CopyOnWriteArrayList
            }
            currentSubRangeStart = currentSubRangeEnd + 1;
        }

        if (actualWorkersDispatched == 0 && !workersForThisJob.isEmpty()) {
            System.out.println("["+masterLogId+"] ("+jobLogId+") Ninguna tarea despachada. Reseteando latch.");
            masterControllerServant.resetForNewJob(0, jobLogId);
        }

        final int finalActualWorkersDispatched = actualWorkersDispatched;
        CompletableFuture.runAsync(() -> {
            System.out.println("["+masterLogId+"-BG] ("+jobLogId+") Hilo esperando " + masterControllerServant.getJobCompletionLatchCount() + " resultados (despachados: " + finalActualWorkersDispatched +").");
            boolean jobCompletedSuccessfully = masterControllerServant.awaitJobCompletion(600_000); // Timeout de 10 minutos
            long jobEndTimeOnMaster = System.currentTimeMillis();
            long[] allPerfectNumbers = masterControllerServant.getAllFoundPerfectNumbers();

            String statusMessage;
            if (finalActualWorkersDispatched == 0 && !workersForThisJob.isEmpty()) {
                statusMessage = "No se pudieron despachar tareas a ningún worker.";
            } else if (workersForThisJob.isEmpty()){
                statusMessage = "No hay workers activos para procesar la solicitud.";
            }
            else {
                statusMessage = jobCompletedSuccessfully ?
                        "Trabajo completado. " + finalActualWorkersDispatched + " worker(s) participaron." :
                        "Trabajo finalizado (timeout o errores). Algunos workers podrían no haber respondido.";
            }
            System.out.println("["+masterLogId+"-BG] ("+jobLogId+") " + statusMessage + " Enviando resultados al cliente.");

            // Loggear tiempo total del maestro al archivo
            try (PrintWriter writer = new PrintWriter(new FileWriter("tiempos_ejecucion.txt", true))) {
                writer.println("Job ID: " + jobLogId + " - Tiempo Total Procesamiento Maestro: " + (jobEndTimeOnMaster - jobStartTimeOnMaster) + " ms");
            } catch (IOException e) {
                System.err.println("["+masterLogId+"-BG] Error escribiendo tiempo total del maestro a archivo: " + e.getMessage());
            }

            try {
                clientNotifierProxy.notifyJobCompletionAsync(jobRange, allPerfectNumbers, statusMessage, jobEndTimeOnMaster - jobStartTimeOnMaster);
            } catch (Exception e) {
                System.err.println("["+masterLogId+"-BG] ("+jobLogId+") Error notificando al cliente: " + e.getMessage());
            }
        });
    }

    private List<WorkerServicePrx> selectWorkersForTask(int numWorkersRequestedByClient, String jobLogId) {
        System.out.println("["+masterLogId+"] ("+jobLogId+") selectWorkersForTask: Workers registrados antes de ping: " + registeredWorkers.size());
        List<WorkerServicePrx> liveWorkersFound = new ArrayList<>();
        List<WorkerServicePrx> workersToRemoveFromGlobalList = new ArrayList<>();

        for (WorkerServicePrx worker : this.registeredWorkers) {
            try {
                worker.ice_ping();
                liveWorkersFound.add(worker);
            } catch (LocalException le) {
                System.err.println("["+masterLogId+"] ("+jobLogId+") selectWorkersForTask: Worker " + getProxyStringRepresentation(worker) + " no respondió (Excepción Local: " + le.getClass().getSimpleName() + "). Removiendo.");
                workersToRemoveFromGlobalList.add(worker);
            } catch (Exception e) {
                System.err.println("["+masterLogId+"] ("+jobLogId+") selectWorkersForTask: Excepción inesperada ping worker " + getProxyStringRepresentation(worker) + ": " + e.getMessage() + ". Removiendo.");
                workersToRemoveFromGlobalList.add(worker);
            }
        }

        if (!workersToRemoveFromGlobalList.isEmpty()) {
            this.registeredWorkers.removeAll(workersToRemoveFromGlobalList);
            System.out.println("["+masterLogId+"] ("+jobLogId+") selectWorkersForTask: Removidos " + workersToRemoveFromGlobalList.size() + " workers inactivos. Registrados ahora: " + this.registeredWorkers.size());
        }

        if (liveWorkersFound.isEmpty()) {
            System.out.println("["+masterLogId+"] ("+jobLogId+") selectWorkersForTask: No hay workers vivos después de pings.");
            return Collections.emptyList();
        }

        int workersToEngage = Math.max(1, numWorkersRequestedByClient);
        workersToEngage = Math.min(workersToEngage, liveWorkersFound.size());
        workersToEngage = Math.min(workersToEngage, MAX_WORKERS_TO_USE_FOR_A_JOB);

        System.out.println("["+masterLogId+"] ("+jobLogId+") Info selección: Vivos=" + liveWorkersFound.size() +
                ", Solicitados=" + numWorkersRequestedByClient +
                ", LímiteSistema=" + MAX_WORKERS_TO_USE_FOR_A_JOB +
                ", WorkersAEvaluar=" + workersToEngage);

        return new ArrayList<>(liveWorkersFound.subList(0, workersToEngage));
    }

    private String getProxyStringRepresentation(ObjectPrx proxy) {
        if (proxy == null) return "null proxy";
        try {
            return proxy.toString().split("\n")[0];
        } catch (Exception e) {
            return proxy.toString().split("\n")[0] + " (ice_toString falló)";
        }
    }
}
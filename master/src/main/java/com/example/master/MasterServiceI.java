package com.example.master;

import perfectNumbersApp.*;
import com.zeroc.Ice.*;

import java.lang.Exception;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class MasterServiceI implements MasterService {

    private final ObjectAdapter adapter;
    private final List<WorkerServicePrx> availableWorkers = new CopyOnWriteArrayList<>();
    private final MasterControllerPrx masterControllerProxy;
    private final MasterControllerI masterControllerServant;

    public MasterServiceI(ObjectAdapter adapter, Communicator communicator) {
        this.adapter = adapter;
        this.masterControllerServant = new MasterControllerI();
        String controllerIdentity = "MasterController-" + UUID.randomUUID().toString();
        this.masterControllerProxy = MasterControllerPrx.uncheckedCast(
                adapter.add(this.masterControllerServant, Util.stringToIdentity(controllerIdentity))
        );
    }

    @Override
    public void registerWorker(WorkerServicePrx workerProxy, Current current) {
        if (workerProxy != null) {
            // Usar toString() como alternativa si ice_toString() da problemas de compilación
            String workerString = workerProxy.toString(); // o workerProxy.ice_toString() si compila
            if (!availableWorkers.stream().anyMatch(p -> p.ice_getIdentity().equals(workerProxy.ice_getIdentity()))) {
                availableWorkers.add(workerProxy);
                System.out.println("[MAESTRO] Worker registrado: " + workerString);
                try {
                    workerProxy.ice_ping();
                    System.out.println("[MAESTRO] Ping al worker " + workerString + " exitoso.");
                } catch (Exception e) {
                    System.err.println("[MAESTRO] Error haciendo ping al worker recién registrado " + workerString + ". Puede que ya no esté disponible. Eliminando.");
                    availableWorkers.remove(workerProxy);
                }
            } else {
                System.out.println("[MAESTRO] Worker " + workerString + " ya estaba registrado.");
            }
        } else {
            System.err.println("[MAESTRO] Intento de registrar un worker nulo.");
        }
    }

    @Override
    public void findPerfectNumbersInRange(
            perfectNumbersApp.Range jobRange,
            ClientNotifierPrx clientNotifierProxy,
            Current current) {
        System.out.println("[MAESTRO] Solicitud recibida para el rango: [" + jobRange.start + ", " + jobRange.end + "]");

        if (clientNotifierProxy == null) {
            System.err.println("[MAESTRO] ClientNotifierPrx es nulo. No se puede notificar al cliente.");
            return;
        }

        List<WorkerServicePrx> currentWorkers = new ArrayList<>(availableWorkers);

        if (currentWorkers.isEmpty()) {
            System.out.println("[MAESTRO] No hay workers disponibles. Notificando al cliente.");
            clientNotifierProxy.notifyJobCompletionAsync(jobRange, new long[0], "No hay workers disponibles.", 0L);
            return;
        }

        long startTime = System.currentTimeMillis();
        masterControllerServant.resetForNewJob(currentWorkers.size());

        long totalNumbersToProcess = jobRange.end - jobRange.start + 1;
        if (totalNumbersToProcess <= 0) {
            clientNotifierProxy.notifyJobCompletionAsync(jobRange, new long[0], "Rango inválido.", 0L);
            masterControllerServant.resetForNewJob(0); // Asegurar que el latch se resetee a 0
            return;
        }

        long numbersPerWorker = Math.max(1, (totalNumbersToProcess + currentWorkers.size() - 1) / currentWorkers.size());
        long currentStart = jobRange.start;
        int workersDispatchedCount = 0;

        System.out.println("[MAESTRO] Dividiendo el trabajo entre " + currentWorkers.size() + " workers. Números aprox por worker: " + numbersPerWorker);

        for (int i = 0; i < currentWorkers.size(); i++) {
            if (currentStart > jobRange.end) {
                break; // Todo el rango ha sido asignado
            }
            workersDispatchedCount++;
            WorkerServicePrx worker = currentWorkers.get(i);
            long currentEnd = Math.min(jobRange.end, currentStart + numbersPerWorker - 1);

            perfectNumbersApp.Range subRange = new perfectNumbersApp.Range(currentStart, currentEnd);
            String workerJobId = "worker-" + i + "-job-" + UUID.randomUUID().toString().substring(0, 8);
            String workerStringRep = worker.toString().split("\n")[0]; // Alternativa a ice_toString()

            System.out.println("[MAESTRO] Enviando subrango [" + subRange.start + ", " + subRange.end + "] al worker " + workerStringRep + " (ID: " + workerJobId + ")");
            try {
                worker.processSubRangeAsync(subRange, this.masterControllerProxy, workerJobId);
            } catch (Exception e) {
                System.err.println("[MAESTRO] Error al enviar trabajo al worker " + workerStringRep + ": " + e.getMessage());
                availableWorkers.remove(worker);
                masterControllerServant.handleWorkerFailure();
            }
            currentStart = currentEnd + 1;
        }

        // Si se despacharon menos tareas que workers disponibles (porque el rango era pequeño)
        // ajustamos el latch para los workers que no recibieron trabajo.
        if (workersDispatchedCount < currentWorkers.size()) {
            int nonDispatchedWorkers = currentWorkers.size() - workersDispatchedCount;
            System.out.println("[MAESTRO] " + nonDispatchedWorkers + " workers no recibieron tareas, ajustando el latch.");
            for (int k = 0; k < nonDispatchedWorkers; k++) {
                masterControllerServant.handleWorkerFailure();
            }
        }
        // Asegurarse de que el latch en masterControllerServant coincida con workersDispatchedCount si se reinició
        // Este ajuste es delicado y depende de cómo se inicialice el latch
        if (masterControllerServant.getJobCompletionLatchCount() > workersDispatchedCount && workersDispatchedCount > 0) {
            // Esto no debería ser necesario si resetForNewJob y handleWorkerFailure se llaman correctamente.
            // Pero como medida de seguridad, si el latch es mayor que los despachados, algo está mal.
            System.err.println("[MAESTRO] Advertencia: El conteo del latch ("+masterControllerServant.getJobCompletionLatchCount()+") es mayor que los workers despachados ("+workersDispatchedCount+"). El comportamiento puede ser inesperado.");
        } else if (workersDispatchedCount == 0 && currentWorkers.size() > 0) {
            // Si había workers pero no se despachó nada (ej. rango inválido ya manejado)
            masterControllerServant.resetForNewJob(0); // Asegura que el latch esté en 0
        }


        CompletableFuture.runAsync(() -> {
            boolean jobCompletedSuccessfully = masterControllerServant.awaitJobCompletion(300_000); // 5 minutos timeout
            long endTime = System.currentTimeMillis();
            long[] allPerfectNumbers = masterControllerServant.getAllFoundPerfectNumbers();
            String statusMessage = jobCompletedSuccessfully ?
                    "Trabajo completado exitosamente." :
                    "Trabajo completado con timeout o errores (algunos workers podrían no haber respondido).";

            System.out.println("[MAESTRO] " + statusMessage + " Enviando resultados consolidados al cliente.");
            try {
                clientNotifierProxy.notifyJobCompletionAsync(jobRange, allPerfectNumbers, statusMessage, endTime - startTime);
            } catch (Exception e) {
                System.err.println("[MAESTRO] Error al notificar al cliente: " + e.getMessage());
            }
        });
    }
}

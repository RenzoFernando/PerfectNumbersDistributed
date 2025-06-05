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

    // Adapter de Ice para registrar el MasterController y exponer servicios
    private final ObjectAdapter adapter;
    // Lista concurrente de proxies a los workers registrados
    private final List<WorkerServicePrx> availableWorkers = new CopyOnWriteArrayList<>();
    // Proxy al servant del MasterController para recibir resultados de los workers
    private final MasterControllerPrx masterControllerProxy;
    // Servant local que maneja la lógica de conteo y acumulación de resultados
    private final MasterControllerI masterControllerServant;

    /**
     * Constructor: crea un nuevo MasterControllerI y lo expone en el adapter.
     * @param adapter Adapter sobre el que registrar el MasterController servant.
     * @param communicator Communicator de Ice (no usado directamente aquí, pero se pasa si se necesita).
     */
    public MasterServiceI(ObjectAdapter adapter, Communicator communicator) {
        this.adapter = adapter;
        this.masterControllerServant = new MasterControllerI();
        // Crear una identidad única para el MasterController servant
        String controllerIdentity = "MasterController-" + UUID.randomUUID().toString();
        // Registrar el MasterControllerI en el adapter y obtener su proxy
        this.masterControllerProxy = MasterControllerPrx.uncheckedCast(
                adapter.add(this.masterControllerServant, Util.stringToIdentity(controllerIdentity))
        );
    }

    /**
     * Método remoto que usan los workers para registrarse con el Maestro.
     * @param workerProxy Proxy al servicio del worker que se registra.
     * @param current Contexto ICE (no usado aquí).
     */
    @Override
    public void registerWorker(WorkerServicePrx workerProxy, Current current) {
        if (workerProxy != null) {
            String workerString = workerProxy.toString(); /// Representación del worker
            // Verificar que no esté ya registrado (comparando identidades ICE)
            if (!availableWorkers.stream().anyMatch(p -> p.ice_getIdentity().equals(workerProxy.ice_getIdentity()))) {
                availableWorkers.add(workerProxy); // Agregar a la lista de workers disponibles
                System.out.println("[MAESTRO] Worker registrado: " + workerString);
                try {
                    // Hacer un ping para confirmar que el worker está disponible
                    workerProxy.ice_ping();
                    System.out.println("[MAESTRO] Ping al worker " + workerString + " exitoso.");
                } catch (Exception e) {
                    // Si falla el ping, eliminar al worker registrado y mostrar advertencia
                    System.err.println("[MAESTRO] Error haciendo ping al worker recién registrado " + workerString + ". Puede que ya no esté disponible. Eliminando.");
                    availableWorkers.remove(workerProxy);
                }
            } else {
                // Si ya estaba en la lista, solo notificarlo
                System.out.println("[MAESTRO] Worker " + workerString + " ya estaba registrado.");
            }
        } else {
            // Si el proxy es nulo, registrar no tiene sentido
            System.err.println("[MAESTRO] Intento de registrar un worker nulo.");
        }
    }

    /**
     * Método remoto que el Cliente invoca para solicitar la búsqueda de números perfectos.
     * Divide el rango en subrangos y envía a los workers disponibles.
     * @param jobRange Rango completo que el Cliente quiere procesar.
     * @param clientNotifierProxy Proxy para notificar al Cliente cuando termine.
     * @param current Contexto ICE (no usado directamente aquí).
     */
    @Override
    public void findPerfectNumbersInRange(
            perfectNumbersApp.Range jobRange,
            ClientNotifierPrx clientNotifierProxy,
            Current current) {
        System.out.println("[MAESTRO] Solicitud recibida para el rango: [" + jobRange.start + ", " + jobRange.end + "]");

        // Validar que tengamos un proxy válido para notificar al Cliente
        if (clientNotifierProxy == null) {
            System.err.println("[MAESTRO] ClientNotifierPrx es nulo. No se puede notificar al cliente.");
            return;
        }

        // Hacer copia de los workers disponibles en este momento
        List<WorkerServicePrx> currentWorkers = new ArrayList<>(availableWorkers);

        // Si no hay workers, notificar al Cliente inmediatamente con arreglo vacío
        if (currentWorkers.isEmpty()) {
            System.out.println("[MAESTRO] No hay workers disponibles. Notificando al cliente.");
            clientNotifierProxy.notifyJobCompletionAsync(jobRange, new long[0], "No hay workers disponibles.", 0L);
            return;
        }

        long startTime = System.currentTimeMillis(); // Marcar tiempo de inicio
        // Preparar el controlador para contar las respuestas de cada worker
        masterControllerServant.resetForNewJob(currentWorkers.size());

        // Calcular cantidad total de números a procesar
        long totalNumbersToProcess = jobRange.end - jobRange.start + 1;

        // Validar rango no negativo o vacío
        if (totalNumbersToProcess <= 0) {
            clientNotifierProxy.notifyJobCompletionAsync(jobRange, new long[0], "Rango inválido.", 0L);
            masterControllerServant.resetForNewJob(0); // Ajustar latch a 0 para no esperar
            return;
        }

        // Calcular cuántos números debe procesar cada worker (redondeo hacia arriba mínimo 1)
        long numbersPerWorker = Math.max(1, (totalNumbersToProcess + currentWorkers.size() - 1) / currentWorkers.size());
        long currentStart = jobRange.start; // Puntero al inicio del primer subrango
        int workersDispatchedCount = 0; // Contador de cuántos workers recibieron tarea

        System.out.println("[MAESTRO] Dividiendo el trabajo entre " + currentWorkers.size() + " workers. Números aprox por worker: " + numbersPerWorker);

        // Asignar subrangos en orden a cada worker
        for (int i = 0; i < currentWorkers.size(); i++) {
            if (currentStart > jobRange.end) {
                break; // Ya se asignó todo el rango
            }
            workersDispatchedCount++;
            WorkerServicePrx worker = currentWorkers.get(i);
            long currentEnd = Math.min(jobRange.end, currentStart + numbersPerWorker - 1); // No pasar el límite superior

            // Crear el objeto Range para el subrango
            perfectNumbersApp.Range subRange = new perfectNumbersApp.Range(currentStart, currentEnd);
            // Generar un ID único para el trabajo en este worker
            String workerJobId = "worker-" + i + "-job-" + UUID.randomUUID().toString().substring(0, 8);
            // Representación string para logging (evitar líneas múltiples)
            String workerStringRep = worker.toString().split("\n")[0]; // Alternativa a ice_toString()

            System.out.println("[MAESTRO] Enviando subrango [" + subRange.start + ", " + subRange.end + "] al worker " + workerStringRep + " (ID: " + workerJobId + ")");
            try {
                // Llamada asíncrona al worker: procesar subrango y luego notificar al MasterController
                worker.processSubRangeAsync(subRange, this.masterControllerProxy, workerJobId);
            } catch (Exception e) {
                // Si falla la llamada al worker, quitarlo de la lista y descontar del latch
                System.err.println("[MAESTRO] Error al enviar trabajo al worker " + workerStringRep + ": " + e.getMessage());
                availableWorkers.remove(worker);
                masterControllerServant.handleWorkerFailure();
            }
            // Actualizar el inicio para el siguiente subrango
            currentStart = currentEnd + 1;
        }

        // Si hay más workers que subrangos, descontar el latch para los que no recibieron tarea
        if (workersDispatchedCount < currentWorkers.size()) {
            int nonDispatchedWorkers = currentWorkers.size() - workersDispatchedCount;
            System.out.println("[MAESTRO] " + nonDispatchedWorkers + " workers no recibieron tareas, ajustando el latch.");
            for (int k = 0; k < nonDispatchedWorkers; k++) {
                masterControllerServant.handleWorkerFailure();
            }
        }

        // Verificación adicional de seguridad: si el latch es mayor que la cantidad de workers despachados
        if (masterControllerServant.getJobCompletionLatchCount() > workersDispatchedCount && workersDispatchedCount > 0) {
            // Esto no debería ser necesario si resetForNewJob y handleWorkerFailure se llaman correctamente.
            // Pero como medida de seguridad, si el latch es mayor que los despachados, algo está mal.
            System.err.println("[MAESTRO] Advertencia: El conteo del latch ("+masterControllerServant.getJobCompletionLatchCount()+") es mayor que los workers despachados ("+workersDispatchedCount+"). El comportamiento puede ser inesperado.");
        } else if (workersDispatchedCount == 0 && currentWorkers.size() > 0) {
            // Si había workers pero no se despachó nada (ya manejado antes), asegurar latch en 0
            masterControllerServant.resetForNewJob(0); // Asegura que el latch esté en 0
        }

        // Iniciar un proceso asíncrono para esperar a los resultados y luego notificar al Cliente
        CompletableFuture.runAsync(() -> {
            boolean jobCompletedSuccessfully = masterControllerServant.awaitJobCompletion(300_000); // 5 minutos timeout
            long endTime = System.currentTimeMillis();
            // Obtener todos los números perfectos encontrados por los workers
            long[] allPerfectNumbers = masterControllerServant.getAllFoundPerfectNumbers();
            String statusMessage = jobCompletedSuccessfully ?
                    "Trabajo completado exitosamente." :
                    "Trabajo completado con timeout o errores (algunos workers podrían no haber respondido).";

            System.out.println("[MAESTRO] " + statusMessage + " Enviando resultados consolidados al cliente.");
            try {
                // Notificar al Cliente con el rango original, los resultados finales, mensaje de estado y tiempo
                clientNotifierProxy.notifyJobCompletionAsync(jobRange, allPerfectNumbers, statusMessage, endTime - startTime);
            } catch (Exception e) {
                System.err.println("[MAESTRO] Error al notificar al cliente: " + e.getMessage());
            }
        });
    }
}

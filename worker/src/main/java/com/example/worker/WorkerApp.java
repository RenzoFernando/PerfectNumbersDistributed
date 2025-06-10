// --- Archivo: worker/src/main/java/com/example/worker/WorkerApp.java ---
package com.example.worker;

import com.zeroc.Ice.*; // Clases de Ice: Communicator, ObjectAdapter, etC
import perfectNumbersApp.*; // Interfaces generadas por Slice
import java.lang.Exception;
import java.util.Arrays; // Para Arrays.toString()

/**
 * Clase principal de la aplicación Worker.
 * Inicializa Ice, registra el servicio y se conecta al Maestro.
 */
public class WorkerApp {
    public static void main(String[] args) {
        System.out.println("[WORKER-APP] Iniciando aplicación Worker...");
        // Inicializar Ice usando el archivo worker.properties
        try (Communicator communicator = Util.initialize(args, "worker.properties")) {
            System.out.println("[WORKER-APP] Communicator Ice inicializado.");

            // Crear adaptador local que expondrá el servicio del Worker
            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");
            System.out.println("[WORKER-APP] ObjectAdapter 'WorkerAdapter' creado.");

            // Crear el servant que implementa la lógica del WorkerService
            WorkerServiceI workerServant = new WorkerServiceI();
            System.out.println("[WORKER-APP] Servant WorkerServiceI instanciado.");

            // Registrar el servant con un ID único generado automáticamente
            ObjectPrx servantProxy = adapter.addWithUUID(workerServant);
            System.out.println("[WORKER-APP] Servant WorkerServiceI añadido al adapter.");

            // Convertir el proxy genérico a WorkerServicePrx para uso de Ice
            WorkerServicePrx workerServicePrx = WorkerServicePrx.uncheckedCast(servantProxy);

            // Activar el adaptador para empezar a recibir llamadas remotas
            adapter.activate();
            System.out.println("[WORKER] Worker iniciado y escuchando en endpoints: " + Arrays.toString(adapter.getEndpoints()));
            System.out.println("[WORKER] Proxy de este worker (para registrar con el maestro): " + workerServicePrx.toString().split("\n")[0]);

            // Obtener proxy al Maestro desde worker.properties
            ObjectPrx baseMasterPrx = communicator.propertyToProxy("MasterService.Proxy");
            if (baseMasterPrx == null) {
                // Si no está en properties, intentar localhost por defecto
                System.err.println("[WORKER-APP] ADVERTENCIA: MasterService.Proxy no encontrado en worker.properties. Intentando conexión directa a localhost:10000.");
                baseMasterPrx = communicator.stringToProxy("MasterService:default -h localhost -p 10000");
                if (baseMasterPrx == null) {
                    System.err.println("[WORKER-APP] ERROR: No se pudo obtener el proxy del maestro por defecto. El worker no se registrará y esperará conexiones directas (si es el caso).");
                }
            }

            if (baseMasterPrx != null) {
                // Convertir el proxy genérico a MasterServicePrx
                MasterServicePrx masterServicePrx = MasterServicePrx.checkedCast(baseMasterPrx);
                if (masterServicePrx == null) {
                    System.err.println("[WORKER-APP] ERROR: Proxy del maestro inválido después del checkedCast. El worker no se registrará.");
                } else {
                    try {
                        // Llamar al Maestro para registrarse como worker disponible
                        System.out.println("[WORKER-APP] Intentando registrarse con el maestro en: " + masterServicePrx.toString().split("\n")[0]);
                        masterServicePrx.registerWorker(workerServicePrx);
                        System.out.println("[WORKER-APP] Registrado exitosamente con el maestro.");
                    } catch (LocalException e) {
                        // Manejar errores de comunicación local de Ice
                        System.err.println("[WORKER-APP] ERROR local de Ice al registrarse con el maestro: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        e.printStackTrace();
                    }
                    catch (Exception e) {
                        // Si falla el registro, mostrar error pero continuar
                        System.err.println("[WORKER-APP] ERROR al registrarse con el maestro: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }

            // Mantener la aplicación viva hasta que se reciba shutdown
            System.out.println("[WORKER-APP] Esperando solicitudes del maestro...");
            communicator.waitForShutdown();
            System.out.println("[WORKER-APP] Worker finalizado después de waitForShutdown.");

        } catch (InitializationException e) {
            // Error grave si falla init de Ice
            System.err.println("[WORKER-APP] FATAL - Error de inicialización de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (LocalException e) {
            // Errores de Ice en tiempo de ejecución
            System.err.println("[WORKER-APP] FATAL - Error local de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // Cualquier otra excepción inesperada
            System.err.println("[WORKER-APP] FATAL - Error inesperado: " + e.getMessage());
            e.printStackTrace();
        }
        // Mensaje final al terminar la aplicación
        System.out.println("[WORKER-APP] Aplicación Worker terminando.");
    }
}
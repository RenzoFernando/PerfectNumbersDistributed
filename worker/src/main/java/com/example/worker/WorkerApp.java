package com.example.worker;

import com.zeroc.Ice.*;
import perfectNumbersApp.*;

import java.lang.Exception;

public class WorkerApp {
    public static void main(String[] args) {
        // Inicializar Ice usando el archivo worker.properties
        try (Communicator communicator = Util.initialize(args, "worker.properties")) {
            // Crear un adapter llamado "WorkerAdapter" (definido en worker.properties)
            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");

            // Crear el servant que implementa la lógica del WorkerService
            WorkerServiceI workerServant = new WorkerServiceI();
            // Registrar el servant en el adapter con un ID único generado automáticamente
            ObjectPrx servantProxy = adapter.addWithUUID(workerServant);
            // Convertir el proxy genérico a WorkerServicePrx para invocaciones ICE
            WorkerServicePrx workerServicePrx = WorkerServicePrx.uncheckedCast(servantProxy);
            // Mostrar en consola que el worker arrancó y cuál es su proxy
            // Se usa toString() y se toma solo la primera línea para que no imprima mucho texto            adapter.activate();
            System.out.println("[WORKER] Worker iniciado. Proxy: " + workerServicePrx.toString().split("\n")[0]);

            // Obtener proxy al servicio del Maestro desde las propiedades de Ice
            ObjectPrx baseMasterPrx = communicator.propertyToProxy("MasterService.Proxy");
            if (baseMasterPrx == null) {
                // Si no está configurado en worker.properties, intentar localhost:10000
                System.err.println("Advertencia: MasterService.Proxy no encontrado en worker.properties. Intentando conexión directa.");
                baseMasterPrx = communicator.stringToProxy("MasterService:default -h localhost -p 10000");
                if (baseMasterPrx == null) {
                    System.err.println("[WORKER] No se pudo obtener el proxy del maestro por defecto. El worker no se registrará.");
                }
            }

            if (baseMasterPrx != null) {
                // Convertir el proxy genérico a MasterServicePrx
                MasterServicePrx masterServicePrx = MasterServicePrx.checkedCast(baseMasterPrx);
                if (masterServicePrx == null) {
                    System.err.println("[WORKER] Proxy del maestro inválido después del checkedCast. El worker no se registrará.");
                } else {
                    try {
                        // Llamar al Maestro para registrarse como worker disponible
                        System.out.println("[WORKER] Intentando registrarse con el maestro...");
                        masterServicePrx.registerWorker(workerServicePrx);
                        System.out.println("[WORKER] Registrado exitosamente con el maestro.");
                    } catch (Exception e) {
                        // Si falla el registro, mostrar error pero continuar (podría reintentar manualmente)
                        System.err.println("[WORKER] Error al registrarse con el maestro: " + e.getMessage());
                    }
                }
            }

            // Quedar a la espera de solicitudes del Maestro (processSubRangeAsync)
            System.out.println("[WORKER] Esperando solicitudes del maestro...");
            communicator.waitForShutdown();
            System.out.println("[WORKER] Worker finalizado.");

        } catch (InitializationException e) {
            // Error inicializando Ice (p. ej. worker.properties mal configurado)
            System.err.println("[WORKER] Error de inicialización de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (LocalException e) {
            // Errores locales de Ice (p. ej. problemas de red)
            System.err.println("[WORKER] Error local de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // Cualquier otro error inesperado
            System.err.println("[WORKER] Error inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

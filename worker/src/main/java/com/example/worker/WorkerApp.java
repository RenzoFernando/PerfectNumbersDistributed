package com.example.worker;

import com.zeroc.Ice.*;
import perfectNumbersApp.*;

import java.lang.Exception;

public class WorkerApp {
    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "worker.properties")) {
            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerAdapter");

            WorkerServiceI workerServant = new WorkerServiceI();
            ObjectPrx servantProxy = adapter.addWithUUID(workerServant);
            WorkerServicePrx workerServicePrx = WorkerServicePrx.uncheckedCast(servantProxy);
            adapter.activate();
            // Usar toString() como alternativa si ice_toString() da problemas de compilación
            System.out.println("[WORKER] Worker iniciado. Proxy: " + workerServicePrx.toString().split("\n")[0]);

            ObjectPrx baseMasterPrx = communicator.propertyToProxy("MasterService.Proxy");
            if (baseMasterPrx == null) {
                System.err.println("Advertencia: MasterService.Proxy no encontrado en worker.properties. Intentando conexión directa.");
                baseMasterPrx = communicator.stringToProxy("MasterService:default -h localhost -p 10000");
                if (baseMasterPrx == null) {
                    System.err.println("[WORKER] No se pudo obtener el proxy del maestro por defecto. El worker no se registrará.");
                }
            }

            if (baseMasterPrx != null) {
                MasterServicePrx masterServicePrx = MasterServicePrx.checkedCast(baseMasterPrx);
                if (masterServicePrx == null) {
                    System.err.println("[WORKER] Proxy del maestro inválido después del checkedCast. El worker no se registrará.");
                } else {
                    try {
                        System.out.println("[WORKER] Intentando registrarse con el maestro...");
                        masterServicePrx.registerWorker(workerServicePrx);
                        System.out.println("[WORKER] Registrado exitosamente con el maestro.");
                    } catch (Exception e) {
                        System.err.println("[WORKER] Error al registrarse con el maestro: " + e.getMessage());
                    }
                }
            }

            System.out.println("[WORKER] Esperando solicitudes del maestro...");
            communicator.waitForShutdown();
            System.out.println("[WORKER] Worker finalizado.");

        } catch (InitializationException e) {
            System.err.println("[WORKER] Error de inicialización de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (LocalException e) {
            System.err.println("[WORKER] Error local de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[WORKER] Error inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

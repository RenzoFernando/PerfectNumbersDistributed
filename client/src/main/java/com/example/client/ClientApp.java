package com.example.client;

import perfectNumbersApp.*; // Importa todas las clases generadas por Ice
import com.zeroc.Ice.*;

import java.lang.Exception;

public class ClientApp {
    public static void main(String[] args) {
        java.util.List<String> extraArgs = new java.util.ArrayList<>();
        // El archivo de propiedades "client.properties" debe estar en src/main/resources
        // o en el directorio desde donde se ejecuta la aplicación.
        try (Communicator communicator = Util.initialize(args, "client.properties", extraArgs)) {

            ObjectAdapter adapter = communicator.createObjectAdapter("ClientNotifierAdapter");
            // Pasamos el communicator al servant para que pueda apagarlo.
            ClientNotifierI notifierServant = new ClientNotifierI(communicator);
            ObjectPrx servantProxy = adapter.addWithUUID(notifierServant);
            ClientNotifierPrx clientNotifierPrx = ClientNotifierPrx.checkedCast(servantProxy);
            adapter.activate();

            ObjectPrx baseMasterPrx = communicator.propertyToProxy("MasterService.Proxy");
            if (baseMasterPrx == null) {
                // Fallback si no está en properties, o puedes lanzar error directamente
                System.err.println("Advertencia: MasterService.Proxy no encontrado en client.properties. Intentando conexión directa.");
                baseMasterPrx = communicator.stringToProxy("MasterService:default -h localhost -p 10000");
                if (baseMasterPrx == null) {
                    throw new Error("Proxy del maestro es nulo. Verifica la configuración o que el maestro esté corriendo.");
                }
            }

            MasterServicePrx masterServicePrx = MasterServicePrx.checkedCast(baseMasterPrx);
            if (masterServicePrx == null) {
                throw new Error("Proxy del maestro inválido después del checkedCast.");
            }

            long startRange = 1;
            long endRange = 10000;

            if (extraArgs.size() >= 2) {
                try {
                    startRange = Long.parseLong(extraArgs.get(0));
                    endRange = Long.parseLong(extraArgs.get(1));
                } catch (NumberFormatException e) {
                    System.err.println("Argumentos de rango inválidos: '" + extraArgs.get(0) + "', '" + extraArgs.get(1) + "'. Usando rango por defecto: " + startRange + "-" + endRange);
                }
            } else {
                System.out.println("Uso: ... com.example.client.ClientApp <inicio> <fin>");
                System.out.println("Usando rango por defecto: " + startRange + "-" + endRange);
            }

            perfectNumbersApp.Range jobRange = new perfectNumbersApp.Range(startRange, endRange); // Usar el tipo correcto
            System.out.println("[CLIENTE] Solicitando números perfectos en el rango [" + jobRange.start + ", " + jobRange.end + "]");

            // findPerfectNumbersInRange no es AMD en Slice, su contraparte async en el proxy es generada automáticamente por Ice.
            masterServicePrx.findPerfectNumbersInRangeAsync(jobRange, clientNotifierPrx);

            System.out.println("[CLIENTE] Petición enviada al maestro. Esperando notificación...");
            communicator.waitForShutdown();
            System.out.println("[CLIENTE] Cliente finalizado.");

        } catch (InitializationException e) {
            System.err.println("[CLIENTE] Error de inicialización de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (LocalException e) {
            System.err.println("[CLIENTE] Error local de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[CLIENTE] Error inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

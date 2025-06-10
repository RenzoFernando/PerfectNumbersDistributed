// --- Archivo: master/src/main/java/com/example/master/MasterApp.java ---
package com.example.master;

import com.zeroc.Ice.*; // Clases principales de Ice: Communicator, ObjectAdapter, etc
import java.lang.Exception;
import java.util.Arrays; // Para mostrar los endpoints como String

/**
 * Clase principal de la aplicación Maestra.
 * Inicializa Ice, crea el servant y espera peticiones de los clientes.
 */
public class MasterApp {
    public static void main(String[] args) {
        System.out.println("[MAESTRO-APP] Iniciando aplicación Maestra...");
        // Inicializar Ice con configuración de master.properties
        try (Communicator communicator = Util.initialize(args, "master.properties")) {
            System.out.println("[MAESTRO-APP] Communicator Ice inicializado.");
            // Crear adaptador de objetos para exponer el servicio Maestro
            ObjectAdapter adapter = communicator.createObjectAdapter("MasterAdapter");
            System.out.println("[MAESTRO-APP] ObjectAdapter 'MasterAdapter' creado.");

            // Instanciar el servant que implementa la lógica del Maestro
            MasterServiceI masterServant = new MasterServiceI(adapter, communicator);
            System.out.println("[MAESTRO-APP] Servant MasterServiceI instanciado.");

            // Registrar el servant en el adaptador con la identidad 'MasterService'
            adapter.add(masterServant, Util.stringToIdentity("MasterService"));
            System.out.println("[MAESTRO-APP] Servant MasterServiceI añadido al adapter con identidad 'MasterService'.");

            // Activar el adaptador para empezar a recibir llamadas remotas
            adapter.activate();
            System.out.println("[MAESTRO] Maestro iniciado y escuchando en endpoints: " + Arrays.toString(adapter.getEndpoints()));


            // Mantener la aplicación viva hasta que se cierre el communicator
            System.out.println("[MAESTRO] Esperando shutdown...");
            communicator.waitForShutdown();
            System.out.println("[MAESTRO] Maestro finalizado después de waitForShutdown.");

        } catch (InitializationException e) {
            // Error en la inicialización de Ice
            System.err.println("[MAESTRO-APP] FATAL - Error de inicialización de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (LocalException e) {
            // Errores locales de Ice
            System.err.println("[MAESTRO-APP] FATAL - Error local de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // Cualquier otro error inesperado
            System.err.println("[MAESTRO-APP] FATAL - Error inesperado: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("[MAESTRO-APP] Aplicación Maestra terminando.");
    }
}
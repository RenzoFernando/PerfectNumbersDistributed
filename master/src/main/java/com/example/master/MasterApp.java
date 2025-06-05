package com.example.master;

import com.zeroc.Ice.*;

import java.lang.Exception;

public class MasterApp {
    public static void main(String[] args) {
        // Inicializar Ice usando el archivo master.properties
        try (Communicator communicator = Util.initialize(args, "master.properties")) {
            // Crear un adapter con nombre "MasterAdapter" (configurado en master.properties)
            ObjectAdapter adapter = communicator.createObjectAdapter("MasterAdapter");

            // Crear el worker que implementa la lógica del Maestro y pasar el adapter y communicator
            MasterServiceI masterServant = new MasterServiceI(adapter, communicator);
            // Registrar el worker con la identidad "MasterService"
            adapter.add(masterServant, Util.stringToIdentity("MasterService"));

            // Activar el adapter para empezar a recibir llamadas remotas
            adapter.activate();
            System.out.println("[MAESTRO] Maestro iniciado y escuchando...");

            // Mantener la aplicación viva hasta que se cierre el communicator
            communicator.waitForShutdown();
            System.out.println("[MAESTRO] Maestro finalizado.");

        } catch (InitializationException e) {
            // Error al inicializar Ice (p. ej. problemas en master.properties)
            System.err.println("[MAESTRO] Error de inicialización de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (LocalException e) {
            // Errores locales de Ice (p. ej. problemas de red o configuración)
            System.err.println("[MAESTRO] Error local de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // Cualquier otro error inesperado
            System.err.println("[MAESTRO] Error inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

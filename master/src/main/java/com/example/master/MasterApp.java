package com.example.master;

import com.zeroc.Ice.*;

import java.lang.Exception;

public class MasterApp {
    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "master.properties")) {
            ObjectAdapter adapter = communicator.createObjectAdapter("MasterAdapter");

            MasterServiceI masterServant = new MasterServiceI(adapter, communicator);
            adapter.add(masterServant, Util.stringToIdentity("MasterService"));

            adapter.activate();
            System.out.println("[MAESTRO] Maestro iniciado y escuchando...");

            communicator.waitForShutdown();
            System.out.println("[MAESTRO] Maestro finalizado.");

        } catch (InitializationException e) {
            System.err.println("[MAESTRO] Error de inicialización de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (LocalException e) {
            System.err.println("[MAESTRO] Error local de Ice: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[MAESTRO] Error inesperado: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

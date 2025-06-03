package com.example.worker;

import com.zeroc.Ice.*;
import perfectNumbersApp.*;

public class WorkerApp {

    public static void main(String[] args) {

        try (Communicator communicator = Util.initialize(args, "worker.properties")) {

            ObjectAdapter adapter = communicator.createObjectAdapter("WorkerService");

            WorkerServiceI servant = new WorkerServiceI();

            WorkerServicePrx prx = WorkerServicePrx.uncheckedCast(
                    adapter.addWithUUID((com.zeroc.Ice.Object) servant));

            adapter.activate();

            System.out.println("[WORKER] Service started and waiting for tasks...");

            try {
                MasterServicePrx master = MasterServicePrx.checkedCast(
                        communicator.stringToProxy("MasterService:default -h localhost -p 10000"));

                if (master != null) {
                    System.out.println("[WORKER] Successfully connected to the Master!");
                    // TODO: Activar cuando el método esté disponible en App.ice!!!!!!!!!
                    // master.registerWorker(prx);
                } else {
                    System.err.println("[WORKER] ERROR: Master proxy is invalid.");
                }

            } catch (com.zeroc.Ice.Exception e) {
                System.err.println("[WORKER] ICE communication error: " + e.getMessage());
                e.printStackTrace();
            } catch (java.lang.Exception e) {
                System.err.println("[WORKER] General error: " + e.getMessage());
                e.printStackTrace();
            }

            communicator.waitForShutdown();

        } catch (LocalException e) {
            System.err.println("[WORKER] FATAL: Communication or configuration error.");
            e.printStackTrace();
        }
    }
}
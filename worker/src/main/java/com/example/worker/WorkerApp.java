package com.example.worker;

import PerfectNumbersApp.*;
import com.zeroc.Ice.*;

public class WorkerApp {
    public static void main(String[] args) {

        try (Communicator ic = Util.initialize(args, "worker.properties")) {
            ObjectAdapter adapter =
                    ic.createObjectAdapterWithEndpoints("WorkerAdapter",
                            "default -h 0.0.0.0 -p 0"); // puerto aleatorio

            WorkerServiceI servant = new WorkerServiceI();
            WorkerServicePrx prx = WorkerServicePrx.uncheckedCast(
                    adapter.addWithUUID(servant));
            adapter.activate();

            //  Registrarse en el maestro
            MasterServicePrx master = MasterServicePrx.checkedCast(
                    ic.stringToProxy("MasterService:default -h localhost -p 10000"));
            if (master != null) {
                System.out.println("Worker registrado en el maestro");
                // master.registerWorker(prx); // aún no expuesto en la interfaz Slice
            }

            ic.waitForShutdown();
        }
    }
}

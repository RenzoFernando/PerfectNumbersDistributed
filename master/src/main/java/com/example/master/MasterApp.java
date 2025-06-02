package com.example.master;

import PerfectNumbersApp.*;
import com.zeroc.Ice.*;

public class MasterApp {
    public static void main(String[] args) {
        try (Communicator ic = Util.initialize(args, "master.properties")) {
            ObjectAdapter adapter =
                    ic.createObjectAdapterWithEndpoints("MasterAdapter",
                            "default -h 0.0.0.0 -p 10000");

            MasterServiceI servant = new MasterServiceI(adapter);
            adapter.add(servant, Util.stringToIdentity("MasterService"));
            adapter.activate();

            System.out.println("Maestro listo (puerto 10000)");
            ic.waitForShutdown();
        }
    }
}

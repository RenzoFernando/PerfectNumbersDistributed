package com.example.client;

import perfectNumbersApp.*;
import com.zeroc.Ice.*;

public class ClientApp {
    public static void main(String[] args) {
        try (Communicator ic = Util.initialize(args, "client.properties")) {

            // 1.  Adaptador para callbacks
            ObjectAdapter adapter = ic.createObjectAdapter("NotifierAdapter");
            ClientNotifierI cb = new ClientNotifierI();
            ObjectPrx prx     = adapter.addWithUUID(cb);
            ClientNotifierPrx notifierPrx = ClientNotifierPrx.checkedCast(prx);
            adapter.activate();

            // 2.  Proxy remoto al maestro
            MasterServicePrx master = MasterServicePrx.checkedCast(
                    ic.stringToProxy("MasterService:default -h localhost -p 10000"));

            if (master == null)
                throw new Error("No se encontró el proxy del maestro");

            // 3.  Enviar tarea
            master.findPerfectNumbersInRange(
                    new Range(1, 100_000), notifierPrx);

            System.out.println("Petición enviada; esperando callback…");
            ic.waitForShutdown();
        }
    }
}

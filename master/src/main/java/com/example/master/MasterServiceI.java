package com.example.master;

import perfectNumbersApp.*;
import com.zeroc.Ice.*;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class MasterServiceI implements MasterService {

    /* ----- estado ----- */
    private final List<WorkerServicePrx> workers = new CopyOnWriteArrayList<>();

    private final MasterControllerI  controllerImpl;
    private final MasterControllerPrx controllerPrx;   // ← proxy guardado
    private final ObjectAdapter adapter;

    /* ----- ctor ----- */
    public MasterServiceI(ObjectAdapter adapter) {
        this.adapter = adapter;

        this.controllerImpl = new MasterControllerI();
        // Guardamos el proxy al registrar el servant:
        ObjectPrx raw = adapter.add(controllerImpl,
                Util.stringToIdentity("MasterController"));
        this.controllerPrx = MasterControllerPrx.uncheckedCast(raw);
    }

    /* ----- API remota ----- */
    @Override
    public void findPerfectNumbersInRange(
            Range jobRange,
            ClientNotifierPrx clientNotifierProxy,
            Current __current) {

        if (workers.isEmpty()) {
            clientNotifierProxy.notifyJobCompletion(
                    jobRange, new long[0], "No hay workers registrados", 0);
            return;
        }

        long t0 = System.currentTimeMillis();

        long total = jobRange.end - jobRange.start + 1;
        long chunk = Math.max(1, total / workers.size());

        int idx = 0;
        for (WorkerServicePrx w : workers) {
            long s = jobRange.start + idx * chunk;
            long e = (idx == workers.size() - 1) ? jobRange.end : s + chunk - 1;
            idx++;

            Range sub = new Range(s, e);
            String wid = UUID.randomUUID().toString();

            w.ice_oneway().processSubRange(sub, controllerPrx, wid);
        }

        // Hilo simple para consolidar (reemplázalo por algo + elegante luego)
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

            long[] result = controllerImpl.collectAndClear();
            clientNotifierProxy.notifyJobCompletion(
                    jobRange, result, "Completado",
                    System.currentTimeMillis() - t0);
        }).start();
    }

    /* ----- registro local, aún no expuesto en Slice ----- */
    public void registerWorker(WorkerServicePrx w) { workers.add(w); }
}

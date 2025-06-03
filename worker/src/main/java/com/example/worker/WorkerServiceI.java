package com.example.worker;

import perfectNumbersApp.*;
import com.zeroc.Ice.*;

import java.util.List;

public class WorkerServiceI implements WorkerService {

    @Override
    public void processSubRange(
            Range subRangeToProcess,
            MasterControllerPrx masterCallbackProxy,
            String workerId,
            Current current) {

        System.out.println("[WORKER " + workerId + "] Procesando subrango: "
                + subRangeToProcess.start + " a " + subRangeToProcess.end);

        List<Long> perfectNumbers = WorkerUtils.getPerfectNumbersInRange(
                subRangeToProcess.start, subRangeToProcess.end);

        long[] result = new long[perfectNumbers.size()];
        for (int i = 0; i < perfectNumbers.size(); i++) {
            result[i] = perfectNumbers.get(i);
        }

        // NumberList result = new NumberList(resultArray); <- No lo reconoce.......

        // TODO: Verificar que esta comunicación sea asincronica (Parece ser que no)
        masterCallbackProxy.submitWorkerResults(workerId, subRangeToProcess, result);

        System.out.println("[WORKER " + workerId + "] Finalizó subrango. Resultados enviados.");
    }
}
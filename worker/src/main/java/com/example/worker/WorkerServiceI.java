package com.example.worker;

import perfectNumbersApp.Range; // Correcto
import perfectNumbersApp.MasterControllerPrx;
import perfectNumbersApp.WorkerService; // La interfaz generada

import com.zeroc.Ice.Current;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class WorkerServiceI implements WorkerService {

    @Override
    public CompletionStage<Void> processSubRangeAsync(
            perfectNumbersApp.Range subRangeToProcess, // Usar el tipo correcto
            MasterControllerPrx masterCallbackProxy,
            String workerId,
            Current current) {

        System.out.println("[WORKER " + workerId + "] Recibido subrango para procesar: [" +
                subRangeToProcess.start + ", " + subRangeToProcess.end + "]");

        return CompletableFuture.runAsync(() -> {
            List<Long> foundPerfectNumbersList = WorkerUtils.getPerfectNumbersInRange(
                    subRangeToProcess.start, subRangeToProcess.end);

            long[] perfectNumbersArray = new long[foundPerfectNumbersList.size()];
            for (int i = 0; i < foundPerfectNumbersList.size(); i++) {
                perfectNumbersArray[i] = foundPerfectNumbersList.get(i);
            }

            System.out.println("[WORKER " + workerId + "] Números perfectos encontrados en el subrango: " +
                    Arrays.toString(perfectNumbersArray));

            if (masterCallbackProxy != null) {
                try {
                    System.out.println("[WORKER " + workerId + "] Enviando resultados al MasterController...");
                    masterCallbackProxy.submitWorkerResultsAsync(workerId, subRangeToProcess, perfectNumbersArray);
                    System.out.println("[WORKER " + workerId + "] Resultados enviados.");
                } catch (Exception e) {
                    System.err.println("[WORKER " + workerId + "] Error al enviar resultados al MasterController: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.err.println("[WORKER " + workerId + "] MasterCallbackProxy es nulo. No se pueden enviar resultados.");
            }
        });
    }
}

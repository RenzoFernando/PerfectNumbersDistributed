package com.example.master;

import perfectNumbersApp.*;
import com.zeroc.Ice.Current;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MasterControllerI implements MasterController {

    private final Map<String, long[]> partials = new ConcurrentHashMap<>();

    @Override
    public void submitWorkerResults(
            String workerId,
            Range processedSubRange,
            long[] perfectNumbersFound,
            Current __current) {

        partials.put(workerId, perfectNumbersFound);
        System.out.printf("Worker %s devolvió %d números perfectos%n",
                workerId, perfectNumbersFound.length);
    }

    /** Une todos los resultados y los devuelve ordenados. */
    public long[] collectAndClear() {
        int total = partials.values().stream()
                .mapToInt(arr -> arr.length).sum();
        long[] merged = new long[total];

        int pos = 0;
        for (long[] arr : partials.values()) {
            System.arraycopy(arr, 0, merged, pos, arr.length);
            pos += arr.length;
        }
        java.util.Arrays.sort(merged);
        partials.clear();
        return merged;
    }
}
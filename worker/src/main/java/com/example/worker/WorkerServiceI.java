package com.example.worker;

import PerfectNumbersApp.*;
import com.zeroc.Ice.*;

import java.util.ArrayList;
import java.util.List;

public class WorkerServiceI implements WorkerService {

    @Override
    public void processSubRange(
            Range subRangeToProcess,
            MasterControllerPrx masterCallbackProxy,
            String workerId,
            Current __current) {

        long[] found = computePerfects(subRangeToProcess.start, subRangeToProcess.end);

        masterCallbackProxy.ice_oneway()
                .submitWorkerResults(workerId, subRangeToProcess, found);
    }

    // ---------- helpers ----------

    private long[] computePerfects(long start, long end) {
        List<Long> tmp = new ArrayList<>();
        for (long n = start; n <= end; n++) {
            if (isPerfect(n)) tmp.add(n);
        }
        long[] out = new long[tmp.size()];
        for (int i = 0; i < tmp.size(); i++) out[i] = tmp.get(i);
        return out;
    }

    private boolean isPerfect(long n) {
        long sum = 1;
        for (long i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                sum += i;
                if (i * i != n) sum += n / i;
            }
        }
        return n > 1 && sum == n;
    }
}

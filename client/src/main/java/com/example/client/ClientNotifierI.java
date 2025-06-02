package com.example.client;

import PerfectNumbersApp.*;
import com.zeroc.Ice.Current;
import java.util.Arrays;

public class ClientNotifierI implements ClientNotifier {

    @Override
    public void notifyJobCompletion(
            Range originalRange,
            long[] perfectNumbers,
            String statusMessage,
            long elapsedTimeMillis,
            Current __current) {

        System.out.printf(
                "JOB DONE!  [%d – %d]  →  %s  (%s, %d ms)%n",
                originalRange.start, originalRange.end,
                Arrays.toString(perfectNumbers),
                statusMessage, elapsedTimeMillis);
    }
}

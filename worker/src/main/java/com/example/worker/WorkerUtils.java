package com.example.worker;

import java.util.List;
import java.util.ArrayList;

public class WorkerUtils {

    public static List<Long> getPerfectNumbersInRange(long start, long end) {
        List<Long> perfects = new ArrayList<>();
        for (long i = start; i <= end; i++) {
            if (isPerfect(i)) {
                perfects.add(i);
            }
        }
        return perfects;
    }

    private static boolean isPerfect(long n) {
        if (n < 2) return false;
        long sum = 1;
        long sqrt = (long) Math.sqrt(n);
        for (long i = 2; i <= sqrt; i++) {
            if (n % i == 0) {
                sum += i;
                long div = n / i;
                if (div != i) sum += div;
            }
        }
        return sum == n;
    }
}
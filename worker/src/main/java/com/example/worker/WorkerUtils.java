package com.example.worker;

import java.util.ArrayList;
import java.util.List;

public class WorkerUtils {

    public static List<Long> getPerfectNumbersInRange(long start, long end) {
        List<Long> perfects = new ArrayList<>();
        // Los números perfectos son positivos, el primero es 6.
        // Ajustar el inicio si es menor que 2 para optimizar un poco.
        long actualStart = Math.max(start, 2L);

        for (long i = actualStart; i <= end; i++) {
            if (isPerfect(i)) {
                perfects.add(i);
            }
        }
        return perfects;
    }

    private static boolean isPerfect(long n) {
        if (n < 6) { // El primer número perfecto es 6.
            return false;
        }
        long sum = 1; // 1 es divisor de todos los números > 1
        // Iterar solo hasta la raíz cuadrada para optimizar
        for (long i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                sum += i;
                if (i * i != n) { // Evitar sumar la raíz cuadrada dos veces si n es un cuadrado perfecto
                    sum += n / i;
                }
            }
        }
        // Un número es perfecto si la suma de sus divisores propios es igual al número.
        // Y n != 1 (aunque ya cubierto por n < 6)
        return sum == n;
    }
}

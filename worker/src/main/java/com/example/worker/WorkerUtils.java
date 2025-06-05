package com.example.worker;

import java.util.ArrayList;
import java.util.List;

public class WorkerUtils {

    /**
     * Obtiene todos los números perfectos en el rango [start, end]
     * @param start Límite inferior (inclusive) del rango
     * @param end   Límite superior (inclusive) del rango
     * @return Lista de números perfectos encontrados en ese rango
     */
    public static List<Long> getPerfectNumbersInRange(long start, long end) {
        List<Long> perfects = new ArrayList<>();
        // Los números perfectos son positivos; el primero es 6
        // Si el usuario pone un valor menor que 2, empezamos desde 2 para optimizar
        long actualStart = Math.max(start, 2L);

        // Recorrer todos los números desde actualStart hasta end
        for (long i = actualStart; i <= end; i++) {
            if (isPerfect(i)) {
                // Si i es perfecto, agregarlo a la lista
                perfects.add(i);
            }
        }
        return perfects;
    }

    /**
     * Verifica si un número n es perfecto
     * Un número es perfecto si la suma de sus divisores propios (excepto él mismo) es igual a n
     * @param n Número a verificar
     * @return true si n es perfecto; false en caso contrario
     */
    private static boolean isPerfect(long n) {
        if (n < 6) {
            // El primer número perfecto es 6. Si n < 6, no puede ser perfecto.
            return false;
        }

        // 1 siempre es divisor de n (>1), así que empezamos con sum = 1
        long sum = 1;
        // Para encontrar divisores, solo iteramos hasta la raíz cuadrada de n
        for (long i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                // Si i divide a n, sumar i
                sum += i;
                // Si i no es la raíz cuadrada exacta (i * i != n), sumar también n/i
                if (i * i != n) {
                    sum += n / i;
                }
            }
        }

        // Si la suma de divisores propios es igual a n, entonces es perfecto.
        return sum == n;
    }
}

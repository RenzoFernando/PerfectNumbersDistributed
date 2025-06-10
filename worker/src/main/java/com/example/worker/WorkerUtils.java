// --- Archivo: worker/src/main/java/com/example/worker/WorkerUtils.java ---
package com.example.worker;

import java.util.ArrayList;
import java.util.List;

/**
 * Clase de utilidades para el Worker.
 * Contiene métodos para detectar números perfectos en un rango.
 */
public class WorkerUtils {
    /**
     * Obtiene todos los números perfectos en el rango [start, end].
     * @param start Límite inferior (inclusive) del rango
     * @param end   Límite superior (inclusive) del rango
     * @return Lista de números perfectos encontrados en ese rango
     */
    public static List<Long> getPerfectNumbersInRange(long start, long end) {
        List<Long> perfects = new ArrayList<>();
        // Los números perfectos empiezan en 6, así que optimizamos rangos menores
        long actualStart = Math.max(start, 2L);

        // Recorrer cada número en el rango y verificar si es perfecto
        for (long i = actualStart; i <= end; i++) {
            if (isPerfect(i)) { // Si i es perfecto, lo añadimos a la lista
                perfects.add(i);
            }
        }
        return perfects;
    }

    /**
     * Verifica si un número n es perfecto.
     * Un número es perfecto si la suma de sus divisores propios (excluyéndose a sí mismo) es igual a n.
     * @param n Número a verificar
     * @return true si n es perfecto; false en caso contrario
     */
    private static boolean isPerfect(long n) {
        if (n < 6) { // El primer número perfecto es 6
            return false;
        }
        long sum = 1; // 1 siempre es divisor de n (si n > 1)
        // Solo iteramos hasta la raíz cuadrada para mejorar eficiencia
        for (long i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                sum += i; // Sumar divisor i
                if (i * i != n) {
                    sum += n / i; // Sumar el divisor par n/i si no es la raíz exacta
                }
            }
        }

        // Si la suma de divisores propios coincide con n, es perfecto
        return sum == n;
    }
}
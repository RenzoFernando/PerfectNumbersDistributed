package com.example.client;

// Asegúrate de importar la clase Range correcta de tu paquete generado por Ice
import perfectNumbersApp.Range; // Correcto
import perfectNumbersApp.ClientNotifier; // La interfaz generada por Slice

import com.zeroc.Ice.Current;
import com.zeroc.Ice.Communicator;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// La clase implementa perfectNumbersApp.ClientNotifier
public class ClientNotifierI implements ClientNotifier {

    private final Communicator communicator;

    public ClientNotifierI(Communicator communicator) {
        this.communicator = communicator;
    }

    // El método debe coincidir con la firma generada por AMD
    @Override
    public CompletionStage<Void> notifyJobCompletionAsync(
            perfectNumbersApp.Range originalRange, // Usar el tipo correcto: perfectNumbersApp.Range
            long[] perfectNumbers, // NameList (sequence<long>) se mapea a long[]
            String statusMessage,
            long elapsedTimeMillis,
            Current current) {

        System.out.println("\n[CLIENTE] == Notificación Recibida ==");
        // Acceder a los campos de perfectNumbersApp.Range
        System.out.println("Rango Original: [" + originalRange.start + " - " + originalRange.end + "]");
        System.out.println("Números Perfectos Encontrados: " + Arrays.toString(perfectNumbers));
        System.out.println("Mensaje de Estado: " + statusMessage);
        System.out.println("Tiempo Transcurrido (ms): " + elapsedTimeMillis);
        System.out.println("================================\n");

        if (this.communicator != null) {
            System.out.println("[CLIENTE] Finalizando el cliente después de la notificación.");
            // Es seguro llamar a shutdown desde un hilo de Ice
            this.communicator.shutdown();
        }
        // Para métodos AMD, se debe devolver un CompletionStage.
        return CompletableFuture.completedFuture(null);
    }
}

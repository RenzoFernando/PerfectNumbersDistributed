// --- Archivo: App.ice ---
// PerfectNumbersDistributed/App.ice
// Define el módulo para la aplicación de números perfectos.
// Este módulo contendrá todas las interfaces y estructuras de datos
// necesarias para la comunicación entre el cliente, el maestro y los trabajadores.
module perfectNumbersApp {

    // Estructura para definir un rango numérico con un inicio y un fin.
    // Se usa 'long' para poder manejar rangos extensos.
    struct Range {
        long start; // Límite inferior del rango.
        long end;   // Límite superior del rango.
    };

    // Secuencia de números 'long', que Ice traducirá a un arreglo long[] en Java.
    // Se usará para listas de números perfectos.
    sequence<long> NumberList;

    // Interfaz que el Maestro usará para notificar al Cliente.
    // El Cliente implementará esta interfaz.
    interface ClientNotifier {
        // Método para notificar al cliente la finalización de un trabajo.
        // Es asíncrono (AMD) para que el Maestro no se bloquee esperando al Cliente.
        // Parámetros:
        //  originalRange: El rango completo solicitado originalmente por el cliente.
        //  perfectNumbers: Lista de números perfectos encontrados.
        //  statusMessage: Un mensaje indicando el estado de la operación (ej. "Completado", "Error").
        //  elapsedTimeMillis: Tiempo total que tomó el procesamiento desde la perspectiva del Maestro.
        ["amd"] void notifyJobCompletion(Range originalRange,
                                         NumberList perfectNumbers,
                                         string statusMessage,
                                         long elapsedTimeMillis);
    };

    // Interfaz que los Trabajadores usarán para enviar resultados parciales al Maestro.
    // El Maestro implementará esta interfaz.
    interface MasterController {
        // Método para que un trabajador envíe los resultados de su subrango.
        // Es asíncrono (AMD) para que el Trabajador no se bloquee esperando al Maestro.
        // Parámetros:
        //  workerId: Identificador único del trabajo/worker que envía el resultado.
        //  processedSubRange: El subrango que este worker procesó.
        //  perfectNumbersFound: Lista de números perfectos encontrados en ese subrango.
        //  workerProcessingTimeMillis: Tiempo que le tomó al worker procesar su subrango.
        ["amd"] void submitWorkerResults(string workerId,
                                         Range processedSubRange,
                                         NumberList perfectNumbersFound,
                                         long workerProcessingTimeMillis);
    };

    // Interfaz que el Maestro usará para enviar trabajo a los Trabajadores.
    // Los Trabajadores implementarán esta interfaz.
    interface WorkerService {
        // Método para que el Maestro asigne un subrango a un trabajador.
        // Es asíncrono (AMD) para que el Maestro no se bloquee esperando al Trabajador.
        // Parámetros:
        //  subRangeToProcess: El subrango específico que este worker debe analizar.
        //  masterCallbackProxy: Proxy al MasterController para que el worker envíe sus resultados.
        //  workerId: Identificador único para este trabajo/subtarea.
        ["amd"] void processSubRange(Range subRangeToProcess,
                                     MasterController* masterCallbackProxy,
                                     string workerId);
    };

    // Interfaz principal del servicio Maestro.
    // El Cliente interactuará con esta interfaz.
    interface MasterService {
        // Método principal invocado por el Cliente para iniciar la búsqueda.
        // Parámetros:
        //  jobRange: El rango completo en el que buscar números perfectos.
        //  clientNotifierProxy: Proxy al ClientNotifier del cliente para enviar la respuesta final.
        //  numWorkersToUse: Número de workers que el cliente desea que se utilicen para esta tarea.
        void findPerfectNumbersInRange(Range jobRange,
                                       ClientNotifier* clientNotifierProxy,
                                       int numWorkersToUse);

        // Método para que los Trabajadores se registren con el Maestro.
        // Parámetros:
        //  workerProxy: Proxy al servicio del worker que se está registrando.
        void registerWorker(WorkerService* workerProxy);

        // Método para que el cliente consulte cuántos workers están activos.
        // Devuelve el número de workers actualmente registrados y que responden a un ping.
        int getActiveWorkerCount();
    };
};
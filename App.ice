// PerfectNumbersDistributed/App.ice
// Módulo que define las interfaces y estructuras para la aplicación de números perfectos
module perfectNumbersApp {

    // Estructura que representa un rango numérico [start, end]
    // Se usa long para permitir rangos grandes
    struct Range {
        long start; // Límite inferior del rango.
        long end;   // Límite superior del rango.
    };

    // Secuencia de long que Ice traduce a long[] en Java
    // Se usa para listas de números perfectos
    sequence<long> NumberList;

    // Interfaz que el Maestro usa para notificar al Cliente
    interface ClientNotifier {
        // AMD (asíncrono sin bloqueo) para no detener al Maestro
        // originalRange: rango completo solicitado
        // perfectNumbers: lista de perfectos encontrados
        // statusMessage: mensaje de estado (ej. "Completado")
        // elapsedTimeMillis: tiempo total desde perspectiva del Maestro
        ["amd"] void notifyJobCompletion(Range originalRange,
                                         NumberList perfectNumbers,
                                         string statusMessage,
                                         long elapsedTimeMillis);
    };

    // Interfaz que los Workers usan para enviar resultados parciales al Maestro
    interface MasterController {
        // AMD para que el Worker no espere respuesta
        // workerId: ID de tarea asignado por el Maestro
        // processedSubRange: rango local procesado por el Worker
        // perfectNumbersFound: perfectos hallados en ese subrango
        // workerProcessingTimeMillis: tiempo que tardó el Worker
        ["amd"] void submitWorkerResults(string workerId,
                                         Range processedSubRange,
                                         NumberList perfectNumbersFound,
                                         long workerProcessingTimeMillis);
    };

    // Interfaz que el Maestro invoca en cada Worker para procesar un subrango
    interface WorkerService {
        // AMD para no bloquear al Maestro
        // subRangeToProcess: rango a procesar
        // masterCallbackProxy: proxy al MasterController para enviar resultados
        // workerId: ID único de la sub-tarea
        ["amd"] void processSubRange(Range subRangeToProcess,
                                     MasterController* masterCallbackProxy,
                                     string workerId);
    };

    // Interfaz principal del Maestro, usada por el Cliente
    interface MasterService {
       // Inicia la búsqueda de perfectos en un rango
       // jobRange: rango completo a analizar
       // clientNotifierProxy: proxy al ClientNotifier para devolver resultados
       // numWorkersToUse: cuántos Workers usar
        void findPerfectNumbersInRange(Range jobRange,
                                       ClientNotifier* clientNotifierProxy,
                                       int numWorkersToUse);

        // Permite a un Worker registrarse con el Maestro
        void registerWorker(WorkerService* workerProxy);

        // Consulta el número de Workers activos (responden a ping)
        int getActiveWorkerCount();
    };
};
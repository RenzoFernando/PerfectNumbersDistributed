// PerfectNumbersDistributed/App.ice
module perfectNumbersApp {

    struct Range {
        long start;
        long end;
    };

    // Ice traduce sequence<long> a long[] en Java.
    // No necesitas definir NumberList como una clase separada en Java.
    sequence<long> NumberList;

    interface ClientNotifier {
        // Notifica al cliente cuando un trabajo se completa.
        // Es oneway para que el maestro no espere al cliente.
        ["amd"] void notifyJobCompletion(Range originalRange,
                                         NumberList perfectNumbers,
                                         string statusMessage,
                                         long elapsedTimeMillis);
    };

    interface MasterController {
        // Los workers envían sus resultados parciales aquí.
        // Es oneway para que el worker no espere al maestro.
        ["amd"] void submitWorkerResults(string workerId,
                                         Range processedSubRange,
                                         NumberList perfectNumbersFound);
    };

    interface WorkerService {
        // El maestro envía un subrango a un worker para procesar.
        // Es oneway para que el maestro no espere al worker.
        ["amd"] void processSubRange(Range subRangeToProcess,
                                     MasterController* masterCallbackProxy,
                                     string workerId);
    };

    interface MasterService {
        // El cliente llama a este método para iniciar la búsqueda.
        void findPerfectNumbersInRange(Range jobRange,
                                       ClientNotifier* clientNotifierProxy);

        // Los workers se registran con el maestro.
        void registerWorker(WorkerService* workerProxy);
    };
};

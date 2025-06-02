// App.ice
module PerfectNumbersApp {

    struct Range {
        long start;
        long end;
    };

    sequence<long> NumberList;

    interface ClientNotifier {
        void notifyJobCompletion(Range originalRange,
                                 NumberList perfectNumbers,
                                 string statusMessage,
                                 long   elapsedTimeMillis);
    };

    interface MasterController {
        void submitWorkerResults(string workerId,
                                 Range  processedSubRange,
                                 NumberList perfectNumbersFound);
    };

    interface WorkerService {
        void processSubRange(Range subRangeToProcess,
                             MasterController* masterCallbackProxy,
                             string workerId);
    };

    interface MasterService {
        void findPerfectNumbersInRange(Range jobRange,
                                       ClientNotifier* clientNotifierProxy);
    };
};

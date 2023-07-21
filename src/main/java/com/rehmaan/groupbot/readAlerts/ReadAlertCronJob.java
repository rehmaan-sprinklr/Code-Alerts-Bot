package com.rehmaan.groupbot.readAlerts;


import com.rehmaan.groupbot.database.CommonQueries;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class ReadAlertCronJob {
    /**
     * Runs the job to refresh alerts every 30 minutes
     *
     * @throws IOException If the Graph API call failed.
     */
    @Scheduled(cron="0 */30 * * * *")
    public static void run(){
        try {
            List<String> allChannel = CommonQueries.getAllChannels();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for(String channel : allChannel) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(()-> GetMessages.refreshAlertsGetMessages(channel));
                futures.add(future);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> System.out.println("All functions completed"))
                    .join();
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
    }
}

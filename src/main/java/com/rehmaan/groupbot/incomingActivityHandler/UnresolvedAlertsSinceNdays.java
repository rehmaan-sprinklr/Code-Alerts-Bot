package com.rehmaan.groupbot.incomingActivityHandler;

import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.TextFormatTypes;
import com.rehmaan.groupbot.adaptiveCard.AdaptiveCard;
import com.rehmaan.groupbot.database.ESClient;
import com.rehmaan.groupbot.database.FilterAlertsQueries;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;



/**
 * A class that handles unresolved alerts since n days commands
 *
 * @author mohammad rehmaan
 */


@Component
public class UnresolvedAlertsSinceNdays {

    @Autowired
    ESClient esClient;


    /**
     * Sends a form to the user to collect the number of days for which they want to get the unresolved alerts.
     *
     * @param turnContext The turn context.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public CompletableFuture<Void> sendFormForDaysInput(TurnContext turnContext) {
        String filePath = "src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/unresolvedSinceNDaysAdaptiveCards/inputForm.json";
        String adaptiveCardJson= ReadFiles.readFileAsString(filePath);
        Activity reply = AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
        return turnContext.sendActivity(reply).thenApply(resourceResponse -> null);
    }


    /**
     * Gets the unresolved alerts for the specified channel ID and number of days.
     *
     * @param channelId The channel ID.
     * @param days The number of days.
     * @param turnContext The turn context.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public CompletableFuture<Void> getUnresolvedAlertsSinceNdays(String channelId, int days, TurnContext turnContext) {
        List<JSONObject> result = null;
        try {
            result = FilterAlertsQueries.unresolvedAlerts(days, channelId);
        }
        catch(Exception ex) {
            SendErrorMessageHandler.sendErrorMessage(turnContext);
        }
        return turnContext.sendActivity(createReplyActivity(result, days)).thenApply(resourceResponse -> null);
    }


    /**
     * Creates an adaptive card that summarizes the unresolved alerts for the specified number of days.
     * render it in a table
     *
     * @param result The list of alerts to summarize.
     * @param days The number of days to summarize the alerts for.
     * @return The adaptive card.
     */
    private Activity createReplyActivity(List<JSONObject> result, int days) {

        StringBuilder replyBuilder = new StringBuilder();
        replyBuilder.append("<h2>THESE ARE THE UNRESOLVED ALERTS OF PAST " + days + " DAYS </h2>");
        replyBuilder.append("<table itemprop=\"copy-paste-table\"><tbody>");
        replyBuilder.append(
                        "<tr><td><strong>S.No.</strong></td>" +
                        "<td><strong>Latest Alert</strong></td>" +
                        "<td><strong>Count</strong></td>" +
                        "<td><strong>Priority</strong></td></tr>"
        );
        int currentIndex = 1;
        for(JSONObject alert : result) {
            String messageUrl = alert.getString("messageUrl");
            int count = alert.getInt("count");
            int priority = alert.getInt("priority");
            String _id = alert.getString("_id");

            replyBuilder.append("<tr><td>" + currentIndex + "</td>");
            replyBuilder.append("<td><a href=\"" + messageUrl + "\">" + _id + "</a></td>");
            replyBuilder.append("<td>" + count + "</td>");
            replyBuilder.append("<td>" + priority + "</td></tr>");

            currentIndex++;
        }
        replyBuilder.append("</tbody></table>");
        Activity reply = MessageFactory.text(replyBuilder.toString());
        reply.setTextFormat(TextFormatTypes.XML);
        return reply;
    }


    /**
     * Sends an adaptive card to the user that replaces the form after submit
     *
     * @param days The number of days that were used to filter the alerts.
     * @return The adaptive card.
     */
    public Activity sendAfterSubmitCard(int days) {
        String cardText = "CodeAlertBot 🤖 showing Alerts for the last " + days + " days";
        String filePath = "src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/afterSubmitCard/afterSubmit.json";
        String adaptiveCardJson = ReadFiles.readFileAsString(filePath).replace("{card-text}", cardText);
        return AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
    }

}

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
 * A class that handles generating summary commands
 *
 * @author mohammad rehmaan
 */

@Component
public class SummaryByFieldsActivityHandler {

    @Autowired
    ESClient esClient;

    /**
     * Sends a form to the user to collect information about the summary they want to generate.
     *
     * @param turnContext The turn context.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public CompletableFuture<Void> sendFormForSummary(TurnContext turnContext) {
        String filePath = "src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/summaryByFieldsAdaptiveCards/inputForm.json";
        String adaptiveCardJson= ReadFiles.readFileAsString(filePath);
        Activity reply = AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
        return turnContext.sendActivity(reply).thenApply(resourceResponse -> null);
    }


    /**
     * Sends a summary of unresolved alerts to the user based on the field and number of days specified.
     *
     * @param field The field to filter the alerts by.
     * @param channelId The channel ID.
     * @param days The number of days to filter the alerts by.
     * @param turnContext The turn context.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public CompletableFuture<Void> sendSummary(String field, String channelId, int days, TurnContext turnContext) {
        List<JSONObject> result = null;
        if(field.equals("environment")) {
            try {
                result = FilterAlertsQueries.getUnresolvedAlertsSummaryByEnvironment(channelId, days);
            }
            catch(Exception ex) {
                return SendErrorMessageHandler.sendErrorMessage(turnContext);
            }
        }
        else if(field.equals("hostInfo")) {
            try {
                result = FilterAlertsQueries.getUnresolvedAlertsSummaryByHostInfo(channelId, days);
            }
            catch(Exception ex) {
                return SendErrorMessageHandler.sendErrorMessage(turnContext);
            }
        }
        else if(field.equals("partnerId")) {
            try {
                result = FilterAlertsQueries.getUnresolvedAlertsSummaryByPartnerId(channelId, days);
            }
            catch(Exception ex) {
                return SendErrorMessageHandler.sendErrorMessage(turnContext);
            }
        }
        if(result == null) {
            return turnContext.sendActivity(MessageFactory.text("Enter a valid field and number of days")).thenApply(resourceResponse -> null);
        }
        return turnContext.sendActivity(createReplyActivity(result, field, days)).thenApply(resourceResponse -> null);
    }


    /**
     * Creates an adaptive card that summarizes the unresolved alerts based on the field and number of days specified.
     *
     * @param result The list of alerts to summarize.
     * @param field The field to filter the alerts by.
     * @param days The number of days to filter the alerts by.
     * @return The adaptive card.
     */
    private Activity createReplyActivity(List<JSONObject> result, String field, int days) {
        // count, messageUrl, field value, priority
        StringBuilder replyBuilder = new StringBuilder();
        replyBuilder.append("<h2>SUMMARY OF ALERTS FOR THE LAST " + days + " days BASED ON DIFFERENT VALUES OF " + field + "</h2>");
        replyBuilder.append("<table itemprop=\"copy-paste-table\"><tbody>");
        replyBuilder.append(
                "<tr><td><strong>" + field + "</strong></td>" +
                        "<td><strong>Latest Alert</strong></td>" +
                        "<td><strong>Count of unique Alerts</strong></td>" +
                        "<td><strong>Priority</strong></td></tr>"
        );
        int currentIndex = 1;
        for(JSONObject alert : result) {
            String messageUrl = alert.getString("messageUrl");
            int count = alert.getInt("count");
            int priority = alert.getInt("priority");
            String value = alert.getString(field);
            String _id = alert.getString("_id");

            replyBuilder.append("<tr><td>" + value + "</td>");
            replyBuilder.append("<td><a href=\"" + messageUrl + "\">" + _id +  "</a></td>");
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
     * sends an activity that replaces the form after it has been submitted
     *
     * @param field The field that was used to filter the alerts.
     * @param days The number of days that were used to filter the alerts.
     * @return The adaptive card.
     */
    public Activity sendAfterSubmitCard(String field, int days) {
        String cardText = "CodeAlertBot 🤖 showing Alerts Summary for the last " + days + " days for different values of " + field;
        String filePath = "src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/afterSubmitCard/afterSubmit.json";
        String adaptiveCardJson = ReadFiles.readFileAsString(filePath).replace("{card-text}", cardText);
        return AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
    }

}

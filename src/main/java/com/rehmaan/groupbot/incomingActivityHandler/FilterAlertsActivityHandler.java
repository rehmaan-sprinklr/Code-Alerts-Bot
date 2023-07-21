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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;


/**
 * A class that handles filtering alerts.
 *
 * @author mohammad rehmaan
 */

@Component
public class FilterAlertsActivityHandler {
    @Autowired
    ESClient esClient;

    /**
     * Sends a message to the user with filtered alerts based on the specified field, value, and days.
     *
     * @param turnContext The turn context.
     * @param field The field to filter alerts by.
     * @param value The value of the field to filter alerts by.
     * @param days The number of days to filter alerts by.
     * @param channelId The channel ID.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public CompletableFuture<Void> sendFilteredAlerts(TurnContext turnContext, String field, String value, int days, String channelId) {
        List<JSONObject> result = null;
        if(field.equals("environment")) {
            try {
                result = FilterAlertsQueries.getByEnvironment(value, days, channelId);
            }
            catch(Exception ex) {
                return SendErrorMessageHandler.sendErrorMessage(turnContext);
            }
        }
        else if(field.equals("partnerId")) {
            try {
                result = FilterAlertsQueries.getByPartnerId(value, days, channelId);
            }
            catch(IOException ex) {
                return SendErrorMessageHandler.sendErrorMessage(turnContext);
            }
        }
        else if(field.equals("service")) {
            try {
                result = FilterAlertsQueries.getByService(value, days, channelId);
            }
            catch(IOException ex) {
                return SendErrorMessageHandler.sendErrorMessage(turnContext);
            }
        }
        else if(field.equals("stackTrace")) {
            try {
                result  = FilterAlertsQueries.getByStackTraceKeywords(value, days, channelId);
            }
            catch(IOException exception) {
                return SendErrorMessageHandler.sendErrorMessage(turnContext);
            }
        }
        if(result == null) {
            return turnContext.sendActivity(MessageFactory.text("Enter a valid field, days")).thenApply(resourceResponse -> null);
        }
        // so now we have an array of objects each object has fields count, messageUrl, priority
        return turnContext.sendActivity(createReplyActivity(result, field, value, days)).thenApply(resourceResponse -> null);
    }


    /**
     * Creates an activity that contains a table of the filtered alerts.
     *
     * @param result The list of filtered alerts.
     * @param field The field that was filtered on.
     * @param value The value of the field that was filtered on.
     * @param days The number of days that were filtered on.
     * @return The activity.
     */
    private Activity createReplyActivity(List<JSONObject> result, String field, String value, int days) {
        // go in esclient and sort the function
        StringBuilder replyBuilder = new StringBuilder();
        replyBuilder.append("<h2>THESE ARE THE UNIQUE ALERTS OF PAST " + days + " days that has " + field + "'s value = " + value + "</h2>");
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
     * Sends a filter form to the user to allow them to filter alerts.
     *
     * @param turnContext The turn context.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public static CompletableFuture<Void> sendFilterForm(TurnContext turnContext) {
        String adaptiveCardJson= ReadFiles.readFileAsString("src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/FilterAlertsAdaptiveCards/inputForm.json");
        Activity reply = AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
        return turnContext.sendActivity(reply).thenApply(resourceResponse -> null);
    }


    /**
     * Sends an adaptive card to the user that replaces the form
     *
     * @param field The field that was filtered on.
     * @param value The value of the field that was filtered on.
     * @param days The number of days that were filtered on.
     * @return The adaptive card.
     */
    public Activity sendAfterSubmitCard(String field, String value, int days) {
        String cardText = "CodeAlertBot 🤖 showing Alerts for the last " + days + " days having " + field + " value = " + value;
        String filePath = "src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/afterSubmitCard/afterSubmit.json";
        String adaptiveCardJson = ReadFiles.readFileAsString(filePath).replace("{card-text}", cardText);
        return AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
    }




}

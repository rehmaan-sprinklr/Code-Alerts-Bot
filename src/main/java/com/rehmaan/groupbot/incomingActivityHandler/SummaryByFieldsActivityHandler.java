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
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class SummaryByFieldsActivityHandler {

    @Autowired
    ESClient esClient;
    public CompletableFuture<Void> sendFormForSummary(TurnContext turnContext) {
        String adaptiveCardJson= "{\n" +
                " \"type\": \"AdaptiveCard\",\n" +
                " \"body\": [\n" +
                " {\n" +
                " \"type\": \"TextBlock\",\n" +
                " \"text\": \"Generate Summary Based On a Specific Field different values form\",\n" +
                " \"weight\": \"Bolder\",\n" +
                " \"size\": \"Medium\",\n" +
                " \"wrap\": true"+
                " },\n" +
                " {\n" +
                " \"type\": \"Input.ChoiceSet\",\n" +
                " \"id\": \"field\",\n" +
                " \"placeholder\": \"Field_Name\",\n" +
                " \"choices\": [ {\"title\" : \"Environment\", \"value\" : \"environment\"},\n" +
                "                 {\"title\" : \"Partner Id\", \"value\" : \"partnerId\"},\n" +
                "                 {\"title\" : \"Host Info\", \"value\" : \"hostInfo\"}\n" +
                "               ]\n," +
                " \"isRequired\": true,\n" +
                " \"errorMessage\": \"Field cannot be empty\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Number\",\n" +
                " \"id\": \"days\",\n" +
                " \"placeholder\": \"Number_of_days\"\n" +
                " }],"+
                " \"actions\": [\n" +
                " {\n" +
                " \"type\": \"Action.Submit\",\n" +
                " \"title\": \"Send\"\n" +
                " }\n" +
                " ],\n" +
                " \"$schema\": \"http://adaptivecards.io/schemas/adaptive-card.json\",\n" +
                " \"version\": \"1.5\"\n" +
                "}";
        Activity reply = AdaptiveCard.createForm(adaptiveCardJson);
        return turnContext.sendActivity(reply).thenApply(resourceResponse -> null);
    }

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

    private Activity createReplyActivity(List<JSONObject> result, String field, int days) {
        // count, messageUrl, field value, priority
        StringBuilder replyBuilder = new StringBuilder();
        replyBuilder.append("<h2>SUMMARY OF ALERTS FOR THE LAST " + days + " days BASED ON DIFFERENT VALUES OF " + field + "</h2>");
        replyBuilder.append("<table itemprop=\"copy-paste-table\"><tbody>");
        replyBuilder.append(
                "<tr><td><strong>" + field + "</strong></td>" +
                        "<td><strong>Latest Alert</strong></td>" +
                        "<td><strong>Count</strong></td>" +
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

    public Activity sendAfterSubmitCard(String field, int days) {
        String cardText = "CodeAlertBot 🤖 showing Alerts Summary for the last " + days + " days for different values of " + field;
        String adaptiveCardJson = "{\n" +
                "  \"type\": \"AdaptiveCard\",\n" +
                "  \"body\": [\n" +
                "    {\n" +
                "      \"type\": \"TextBlock\",\n" +
                "      \"size\": \"medium\",\n" +
                "      \"weight\": \"bolder\",\n" +
                "      \"text\":\"" + cardText +  "\",\n" +
                "      \"style\": \"heading\",\n" +
                "      \"wrap\": true\n" +
                "    }" +
                "   ]," +
                "  \"$schema\": \"http://adaptivecards.io/schemas/adaptive-card.json\",\n" +
                "  \"version\": \"1.5\"\n" +
                "}";
        return AdaptiveCard.createForm(adaptiveCardJson);
    }

}

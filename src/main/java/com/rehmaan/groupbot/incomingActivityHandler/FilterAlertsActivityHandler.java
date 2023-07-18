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
import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;


@Component
public class FilterAlertsActivityHandler {
    @Autowired
    ESClient esClient;
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


    public CompletableFuture<Void> sendFilterForm(TurnContext turnContext) {
        String adaptiveCardJson= "{\n" +
                " \"type\": \"AdaptiveCard\",\n" +
                " \"body\": [\n" +
                " {\n" +
                " \"type\": \"TextBlock\",\n" +
                " \"text\": \"Filter Alert Form\",\n" +
                " \"weight\": \"Bolder\",\n" +
                " \"size\": \"Medium\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.ChoiceSet\",\n" +
                " \"id\": \"field\",\n" +
                " \"placeholder\": \"Field_Name\",\n" +
                " \"choices\": [ {\"title\" : \"Environment\", \"value\" : \"environment\"},\n" +
                "                 {\"title\" : \"Partner Id\", \"value\" : \"partnerId\"},\n" +
                "                 {\"title\" : \"StackTrace keywords\", \"value\" : \"stackTrace\"},\n" +
                "                 {\"title\" : \"Service\", \"value\" : \"service\"}\n" +
                "               ]\n," +
                " \"isRequired\": true,\n" +
                " \"errorMessage\": \"Field cannot be empty\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Text\",\n" +
                " \"id\": \"value\",\n" +
                " \"isRequired\" : true,\n"+
                " \"errorMessage\": \"Value cannot be empty\",\n" +
                " \"placeholder\": \"Value\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Number\",\n" +
                " \"id\": \"days\",\n" +
                " \"placeholder\": \"Number_of_days\",\n" +
                " \"isRequired\" : true,\n"+
                " \"errorMessage\": \"Value cannot be empty\"\n" +
                " }],\n"+
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


    public Activity sendAfterSubmitCard(String field, String value, int days) {
        String cardText = "CodeAlertBot 🤖 showing Alerts for the last " + days + " days having " + field + " value = " + value;
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

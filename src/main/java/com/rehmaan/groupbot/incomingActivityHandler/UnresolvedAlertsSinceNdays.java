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


@Component
public class UnresolvedAlertsSinceNdays {

    @Autowired
    ESClient esClient;

    public CompletableFuture<Void> sendFormForDaysInput(TurnContext turnContext) {
        String adaptiveCardJson= "{\n" +
                " \"type\": \"AdaptiveCard\",\n" +
                " \"body\": [\n" +
                " {\n" +
                " \"type\": \"TextBlock\",\n" +
                " \"text\": \"Unresolved Alerts Since N days\",\n" +
                " \"weight\": \"Bolder\",\n" +
                " \"size\": \"Medium\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Number\",\n" +
                " \"id\": \"days\",\n" +
                " \"placeholder\": \"NUMBER_OF_DAYS\"\n" +
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

    public Activity sendAfterSubmitCard(int days) {
        String cardText = "CodeAlertBot 🤖 showing Alerts for the last " + days + " days";
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

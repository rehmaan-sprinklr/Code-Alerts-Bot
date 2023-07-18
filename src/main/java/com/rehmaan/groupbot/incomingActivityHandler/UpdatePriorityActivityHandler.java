package com.rehmaan.groupbot.incomingActivityHandler;

import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.Activity;
import com.rehmaan.groupbot.adaptiveCard.AdaptiveCard;
import com.rehmaan.groupbot.database.ESClient;
import com.rehmaan.groupbot.database.PriorityUpdateQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;


@Component
public class UpdatePriorityActivityHandler {
    @Autowired
    ESClient esClient;
    public CompletableFuture<Void> sendFormForPriorityInput(TurnContext turnContext) {
        String adaptiveCardJson= "{\n" +
                " \"type\": \"AdaptiveCard\",\n" +
                " \"body\": [\n" +
                " {\n" +
                " \"type\": \"TextBlock\",\n" +
                " \"text\": \"Priority Update Form\",\n" +
                " \"weight\": \"Bolder\",\n" +
                " \"size\": \"Medium\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Text\",\n" +
                " \"id\": \"keyword\",\n" +
                " \"placeholder\": \"KEYWORD\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Number\",\n" +
                " \"id\": \"value\",\n" +
                " \"placeholder\": \"VALUE\"\n" +
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


    public CompletableFuture<Void> updatePriority(String keyword, int value, String channelId,  TurnContext turnContext) {
        try{
            PriorityUpdateQueries.updatePriority(keyword, value, channelId);
        }
        catch(Exception ex) {
            SendErrorMessageHandler.sendErrorMessage(turnContext);
        }
        return turnContext.sendActivity(MessageFactory.text("Successfully updated keyword's priority value")).thenApply(resourceResponse -> null);
    }


    public Activity sendAfterSubmitCard(String keyword, int value) {
        String cardText = "CodeAlertBot 🤖 priority set success ✅ ✅";
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


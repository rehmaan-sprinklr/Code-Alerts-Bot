package com.rehmaan.groupbot.incomingActivityHandler;

import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.HeroCard;
import com.rehmaan.groupbot.adaptiveCard.AdaptiveCard;
import com.rehmaan.groupbot.database.CommonQueries;
import com.rehmaan.groupbot.database.ESClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Component
public class MarkAsResolvedHandler {

    @Autowired
    ESClient esClient;

    private final int lengthOfEsId = 20;

    public String getId(String text) throws Exception {
        if(text.contains("thread.tacv2/")) {
            int left = text.indexOf("thread.tacv2/") + "thread.tacv2/".length();
            int right = text.indexOf("?");
            String id = text.substring(left, right);
            return id;
        }

        else if(text.contains("esId:")) {
            int left = text.indexOf("esId:") + "esId:".length();
            int right = left + lengthOfEsId;
            String id = text.substring(left, right);
            return id;
        }
        throw new Exception("invalid input given by user");
    }

    public CompletableFuture<Void> sendMarkAsResolvedForm(TurnContext turnContext) {
        String adaptiveCardJson= "{\n" +
                " \"type\": \"AdaptiveCard\",\n" +
                " \"body\": [\n" +
                " {\n" +
                " \"type\": \"TextBlock\",\n" +
                " \"text\": \"Mark an alert as resolved\",\n" +
                " \"weight\": \"Bolder\",\n" +
                " \"size\": \"Medium\"\n" +
                " },\n" +
                " {\n" +
                " \"type\": \"Input.Text\",\n" +
                " \"id\": \"messageUrl\",\n" +
                " \"isRequired\" : true,\n"+
                " \"errorMessage\": \"Value cannot be empty\",\n" +
                " \"placeholder\": \"Message URL\"\n" +
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

    public CompletableFuture<Void> markAsResolved(TurnContext turnContext, String message, String channelId) {
        try {
            String messageId = getId(message);
            if(message.contains("thread.tacv2/")) {
                CommonQueries.deleteByMessageId(messageId, true, channelId);
            }
            else {
                CommonQueries.deleteByMessageId(messageId, false, channelId);
            }
        }
        catch (IOException ex) {
            return SendErrorMessageHandler.sendErrorMessage(turnContext);
        }
        catch(Exception ex) {
            return turnContext.sendActivity(MessageFactory.text("invalid input given by the user")).thenApply(resourceResponse -> null);
        }

        HeroCard heroCard = new HeroCard();
        heroCard.setText("Thanks!! If the URL corresponds to a valid alert it will be marked as resolved");
        Activity replyActivity = MessageFactory.attachment(heroCard.toAttachment());
        return turnContext.sendActivity(replyActivity).thenApply(resourceResponse -> null);
    }

    public Activity sendAfterSubmitCard() {
        String cardText = "CodeAlertBot 🤖 : Alert is marked as resolved and removed from the database ✅✅";
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

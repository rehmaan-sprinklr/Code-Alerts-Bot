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



/**
 * A class that is used to mark  alerts as resolved.
 *
 * @author mohammad rehmaan
 */

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

        else if(text.contains("Id:")) {
            int left = text.indexOf("Id:") + "Id:".length();
            int right = left + lengthOfEsId;
            String id = text.substring(left, right);
            return id;
        }
        throw new Exception("invalid input given by user");
    }


    /**
     * Sends a form to the user to allow them to mark an alert as resolved.
     *
     * @param turnContext The turn context.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public CompletableFuture<Void> sendMarkAsResolvedForm(TurnContext turnContext) {
        String filePath= "src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/markAsResolvedAdaptiveCards/inputForm.json";
        String adaptiveCardJson = ReadFiles.readFileAsString(filePath);
        Activity reply = AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
        return turnContext.sendActivity(reply).thenApply(resourceResponse -> null);
    }



    /**
     * Marks an alert as resolved.
     *
     * @param turnContext The turn context.
     * @param message The message URL.
     * @param channelId The channel ID.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
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


    /**
     * Sends an adaptive card to the user to notify them that the alert has been marked as resolved and removed from the database.
     *
     * @return The adaptive card.
     */
    public Activity sendAfterSubmitCard() {
        String cardText = "CodeAlertBot 🤖 : Alert is marked as resolved and removed from the database ✅✅";
        String filePath = "src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/afterSubmitCard/afterSubmit.json";
        String adaptiveCardJson = ReadFiles.readFileAsString(filePath).replace("{card-text}", cardText);
        return AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
    }

}

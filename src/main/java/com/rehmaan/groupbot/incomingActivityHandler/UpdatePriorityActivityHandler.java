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



/**
 * A class that updates priority values of some keywords and updates the priority of alerts in the database
 *
 * @author mohammad rehmaan
 */

@Component
public class UpdatePriorityActivityHandler {
    @Autowired
    ESClient esClient;

    /**
     * Sends a form to the user to collect the keyword and value for updating the priority of alerts.
     *
     * @param turnContext The turn context.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public CompletableFuture<Void> sendFormForPriorityInput(TurnContext turnContext) {
        String filePath = "src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/updatePriorityAdaptiveCards/inputForm.json";
        String adaptiveCardJson= ReadFiles.readFileAsString(filePath);
        Activity reply = AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
        return turnContext.sendActivity(reply).thenApply(resourceResponse -> null);
    }


    /**
     * Updates the priority of an alert based on the keyword and value provided by the user.
     *
     * @param keyword The keyword of the alert.
     * @param value The new priority value.
     * @param channelId The channel ID.
     * @param turnContext The turn context.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public CompletableFuture<Void> updatePriority(String keyword, int value, String channelId,  TurnContext turnContext) {
        try{
            PriorityUpdateQueries.updatePriority(keyword, value, channelId);
        }
        catch(Exception ex) {
            SendErrorMessageHandler.sendErrorMessage(turnContext);
        }
        return turnContext.sendActivity(MessageFactory.text("Successfully updated keyword's priority value")).thenApply(resourceResponse -> null);
    }


    /**
     * Sends an adaptive card to the user to show the success message of updating the priority. it replaces the form
     *
     * @param keyword The keyword of the alert.
     * @param value The new priority value.
     * @return The adaptive card.
     */
    public Activity sendAfterSubmitCard(String keyword, int value) {
        String cardText = "CodeAlertBot 🤖 priority set success ✅ ✅";
        String filePath = "src/main/java/com/rehmaan/groupbot/adaptiveCard/AdaptiveCardJSON/afterSubmitCard/afterSubmit.json";
        String adaptiveCardJson = ReadFiles.readFileAsString(filePath).replace("{card-text}", cardText);
        return AdaptiveCard.createAdaptiveCard(adaptiveCardJson);
    }


}


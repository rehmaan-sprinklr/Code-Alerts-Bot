package com.rehmaan.groupbot.incomingActivityHandler;

import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.Mention;
import com.rehmaan.groupbot.database.ESClient;
import com.rehmaan.groupbot.database.OwnersHandlingQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


/**
 * A class that handles assigning of owners
 *
 * @author mohammad rehmaan
 */

@Component
public class OwnersAssignActivityHandler {
    @Autowired
    ESClient esClient;

    private int lengthOfEsId= 20;

    public String getId(String text) throws Exception {
        if(text.contains("thread.tacv2/")) {
            int left = text.indexOf("thread.tact2/") + "thread.tacv2/".length();
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
     * Assigns owners to an alert.
     *
     * @param turnContext The turn context.
     * @param channelId The channel ID.
     * @return A CompletableFuture that will be completed when the owners have been assigned.
     */
    public CompletableFuture<Void> assignOwners(TurnContext turnContext, String channelId) {
        String messageText = turnContext.getActivity().getText();
        String id = "";

        try {
            id = getId(messageText);
        }
        catch(Exception ex) {
            return turnContext.sendActivity(MessageFactory.text("invalid input by user")).thenApply(resourceResponse -> null);
        }

        List<Mention> mentions =  turnContext.getActivity().getMentions();
        List<String> owners = new ArrayList<>();
        for(Mention mention : mentions) {
            if(mention.getMentioned().getName().equals("CodeAlertBot")) {
                continue;
            }
            owners.add(mention.getMentioned().getId() + "#" + mention.getMentioned().getName());
        }

        try {
            if(messageText.contains("thread.tacv2/")) {
                OwnersHandlingQueries.assignOwners(owners, channelId, id, true);
            }
            else {
                OwnersHandlingQueries.assignOwners(owners, channelId, id, false);
            }
            return turnContext.sendActivity(MessageFactory.text("SuccessFully assigned owners")).thenApply(resourceResponse -> null);
        }
        catch(Exception ex) {
            return SendErrorMessageHandler.sendErrorMessage(turnContext);
        }
    }



    /**
     * Removes owners from an alert.
     *
     * @param turnContext The turn context.
     * @param channelId The channel ID.
     * @return A CompletableFuture that will be completed when the owners have been removed.
     */
    public CompletableFuture<Void> removeOwners(TurnContext turnContext, String channelId) {
        String messageText = turnContext.getActivity().getText();
        String id = "";

        try {
            id = getId(messageText);
        }
        catch(Exception ex) {
            return turnContext.sendActivity(MessageFactory.text("invalid input by user")).thenApply(resourceResponse -> null);
        }

        List<Mention> mentions =  turnContext.getActivity().getMentions();
        List<String> owners = new ArrayList<>();
        for(Mention mention : mentions) {
            if(mention.getMentioned().getName().equals("CodeAlertBot")) {
                continue;
            }
            owners.add(mention.getMentioned().getId() + "#" + mention.getMentioned().getName());
        }

        try {
            if(messageText.contains("thread.tacv2/")) {
                OwnersHandlingQueries.removeOwners(owners, id, channelId, true);
            }
            else {
                OwnersHandlingQueries.removeOwners(owners, id, channelId,  false);
            }
            return turnContext.sendActivity(MessageFactory.text("SuccessFully removed owners")).thenApply(resourceResponse -> null);
        }
        catch(Exception ex) {
            return SendErrorMessageHandler.sendErrorMessage(turnContext);
        }
    }


}

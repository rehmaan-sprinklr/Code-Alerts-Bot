package com.rehmaan.groupbot.incomingActivityHandler;

import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;


/**
 * A class that sends error messages in case some exception is encountered
 *
 * @author mohammad rehmaan
 */

public class SendErrorMessageHandler {

    /**
     * Sends an error message to the user.
     *
     * @param turnContext The turn context.
     * @return A CompletableFuture that will be completed when the message has been sent.
     */
    public static CompletableFuture<Void> sendErrorMessage(TurnContext turnContext) {
        return turnContext.sendActivity(MessageFactory.text("Sorry there is some internal server error")).thenApply(resourceResponse -> null);
    }
}

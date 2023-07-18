package com.rehmaan.groupbot.incomingActivityHandler;

import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

public class SendErrorMessageHandler {
    public static CompletableFuture<Void> sendErrorMessage(TurnContext turnContext) {
        return turnContext.sendActivity(MessageFactory.text("Sorry there is some internal server error")).thenApply(resourceResponse -> null);
    }
}

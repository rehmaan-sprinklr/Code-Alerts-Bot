package com.rehmaan.groupbot.adaptiveCard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.Attachment;
import org.elasticsearch.index.rankeval.RankEvalPlugin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component
public class AdaptiveCard {

    public static Activity createForm(String adaptiveCardJson) {
        Attachment cardAttachment = new Attachment();
        cardAttachment.setContentType("application/vnd.microsoft.card.adaptive");
        try {
            cardAttachment.setContent(new ObjectMapper().readTree(adaptiveCardJson));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        Activity reply = MessageFactory.attachment(cardAttachment);

        Map<String, Object> channelData = new HashMap<>();
        channelData.put("isAdaptiveCard", true);
        reply.setChannelData(channelData);

        return reply;
    }
}

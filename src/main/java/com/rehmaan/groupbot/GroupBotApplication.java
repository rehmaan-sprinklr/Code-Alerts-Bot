package com.rehmaan.groupbot;


import com.microsoft.bot.builder.Bot;
import com.microsoft.bot.integration.AdapterWithErrorHandler;
import com.microsoft.bot.integration.BotFrameworkHttpAdapter;
import com.microsoft.bot.integration.spring.BotController;
import com.microsoft.bot.integration.spring.BotDependencyConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.microsoft.bot.integration.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Import({BotController.class})
@EnableScheduling
public class GroupBotApplication extends BotDependencyConfiguration {
	public static void main(String[] args) throws Exception {
		SpringApplication.run(GroupBotApplication.class, args);
	}

	@Bean
	public Bot getBot(Configuration configuration) {
		return new com.rehmaan.groupbot.Bot(configuration);
	}

	@Override
	public BotFrameworkHttpAdapter getBotFrameworkHttpAdaptor(Configuration configuration) {
		return new AdapterWithErrorHandler(configuration);
	}
}

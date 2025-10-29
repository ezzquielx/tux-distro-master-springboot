package dev.ezeAI.ezeAI.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import dev.ezeAI.ezeAI.bot.TelegramBot;

@Slf4j
@Configuration
public class BotConfig {

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBot telegramBot) {
        TelegramBotsApi botsApi = null;
        try {
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBot);
            log.info("Bot de Telegram registrado exitosamente!");
        } catch (TelegramApiException e) {
            log.error("Error registrando el bot de Telegram: ", e);
        }
        return botsApi;
    }
}

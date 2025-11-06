package dev.ezeAI.ezeAI.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ezeAI.ezeAI.model.Question;
import dev.ezeAI.ezeAI.model.UserState;
import dev.ezeAI.ezeAI.bot.AIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private static final Map<String, String> COMMANDS = Map.of(
            "/start", "¡Hola %s! 🐧 Soy Tux, tu asistente Linux.\n\n" +
                    "Comandos: /cuestionario /help /about /repo\n" +
                    "¡Preguntame cualquier cosa sobre Linux!",
            "/help", "🤖 Comandos:\n/start /cuestionario /help /about /repo\n\n" +
                    "Pregunta ejemplo: \"¿Qué distro para gaming?\"",
            "/about", "ℹ️ Tux Bot - Asistente Linux con IA\n" +
                    "Spring Boot 3.5.6 + Java 21 + Groq API\nDesarrollado por: Ezequiel",
            "/repo", "🐙 ¡Código Abierto! Ver repositorio en GitHub https://github.com/ezzquielx/tux-distro-master-springboot.git"
    );

    private final String botUsername;
    private final AIService aiService;
    private final Map<Long, UserState> userStates = new HashMap<>();
    private final List<Question> questions = new ArrayList<>();

    public TelegramBot(@Value("${telegram.bot.token}") String botToken,
                       @Value("${telegram.bot.username}") String botUsername,
                       AIService aiService) {
        super(botToken);
        this.botUsername = botUsername;
        this.aiService = aiService;
        loadQuestions();
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();
        String userName = update.getMessage().getFrom().getFirstName();

        log.info("Mensaje de {}: {}", userName, messageText);

        if (messageText.startsWith("/")) {
            handleCommand(chatId, messageText, userName);
        } else {
            handleUserMessage(chatId, messageText, userName);
        }
    }

    private void handleCommand(Long chatId, String command, String userName) {
        if (command.equals("/cuestionario")) {
            startQuestionnaire(chatId, userName);
            return;
        }

        String response = COMMANDS.getOrDefault(command.toLowerCase(),
                "❌ Comando no reconocido. Usa /help");

        sendMessage(chatId, String.format(response, userName));
    }

    private void startQuestionnaire(Long chatId, String userName) {
        if (questions.isEmpty()) {
            sendMessage(chatId, "❌ Error cargando cuestionario. Preguntame directamente sobre Linux.");
            return;
        }

        UserState state = userStates.computeIfAbsent(chatId, k -> new UserState());
        state.setInQuestionnaire(true);
        state.setQuestionIndex(0);
        state.getResponses().clear();

        sendMessage(chatId, String.format("🎯 Cuestionario (%d preguntas)\n\nPregunta 1/%d:\n%s",
                questions.size(), questions.size(), questions.get(0).getPregunta()));
    }

    private void handleUserMessage(Long chatId, String messageText, String userName) {
        UserState state = userStates.get(chatId);

        if (state != null && state.isInQuestionnaire()) {
            if (aiService.isQuestion(messageText)) {
                handleQuestionDuringQuestionnaire(chatId, messageText, userName, state);
            } else {
                handleQuestionnaireResponse(chatId, messageText, userName, state);
            }
        } else {
            handleFreeQuestion(chatId, messageText, userName);
        }
    }

    private void handleQuestionDuringQuestionnaire(Long chatId, String messageText, String userName, UserState state) {
        try {
            sendTypingAction(chatId);
            String context = String.format("Usuario en cuestionario Linux. Pregunta %d/%d. Responde conciso.",
                    state.getQuestionIndex() + 1, questions.size());

            String response = aiService.generateResponseWithContext(messageText, userName, context);
            sendMessage(chatId, response);

            String continueMsg = String.format("🔄 ¿Continuar cuestionario?\n\nPregunta %d/%d:\n%s",
                    state.getQuestionIndex() + 1, questions.size(),
                    questions.get(state.getQuestionIndex()).getPregunta());
            sendMessage(chatId, continueMsg);
        } catch (Exception e) {
            log.error("Error en pregunta durante cuestionario: ", e);
            sendMessage(chatId, "❌ Error. ¿Continuar cuestionario?");
        }
    }

    private void handleQuestionnaireResponse(Long chatId, String messageText, String userName, UserState state) {
        int index = state.getQuestionIndex();

        state.getResponses().put(questions.get(index).getPregunta(), messageText);
        state.setQuestionIndex(index + 1);

        if (index + 1 < questions.size()) {
            sendMessage(chatId, String.format("✅ Guardado.\n\nPregunta %d/%d:\n%s",
                    index + 2, questions.size(), questions.get(index + 1).getPregunta()));
        } else {
            state.setInQuestionnaire(false);
            generateFinalRecommendation(chatId, state.getResponses(), userName);
        }
    }

    private void generateFinalRecommendation(Long chatId, Map<String, String> responses, String userName) {
        try {
            sendTypingAction(chatId);
            String prompt = "Basándote en estas respuestas, recomendá la mejor distribución Linux:\n\n" + responses;
            String recommendation = aiService.generateResponseWithContext(prompt, userName, "Recomendación final");

            sendMessage(chatId, String.format("🎉 ¡Completado!\n\n%s\n\n🔄 Usa /cuestionario para reiniciar o /help", recommendation));
        } catch (Exception e) {
            log.error("Error generando recomendación: ", e);
            sendMessage(chatId, "❌ Error generando recomendación. Preguntá directamente sobre distribuciones.");
        }
    }

    private void handleFreeQuestion(Long chatId, String messageText, String userName) {
        try {
            sendTypingAction(chatId);
            String response = aiService.generateResponse(messageText, userName);
            sendMessage(chatId, response + "\n\n💡 Tip: Usa /cuestionario para recomendación personalizada.");
        } catch (Exception e) {
            log.error("Error en pregunta libre: ", e);
            sendMessage(chatId, "❌ Error procesando pregunta. Inténtalo de nuevo.");
        }
    }

    private void loadQuestions() {
        try {
            ClassPathResource resource = new ClassPathResource("preguntas.json");
            if (resource.exists()) {
                String jsonContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                questions.addAll(List.of(new ObjectMapper().readValue(jsonContent, Question[].class)));
                log.info("Preguntas cargadas: {}", questions.size());
            } else {
                createDefaultQuestions();
            }
        } catch (IOException e) {
            log.error("Error cargando preguntas: ", e);
            createDefaultQuestions();
        }
    }

    private void createDefaultQuestions() {
        String[] defaultQuestions = {
                "¿Cuál es tu nivel de experiencia con Linux? (principiante/intermedio/avanzado)",
                "¿Para qué vas a usar principalmente Linux? (trabajo/gaming/programación/uso general)",
                "¿Qué tan potente es tu computadora? (alta/media/baja potencia)"
        };

        Arrays.stream(defaultQuestions).forEach(q -> {
            Question question = new Question();
            question.setPregunta(q);
            questions.add(question);
        });
    }

    private void sendMessage(Long chatId, String text) {
        try {
            execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
            log.info("Mensaje enviado a chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error enviando mensaje: ", e);
        }
    }

    private void sendTypingAction(Long chatId) {
        try {
            execute(SendChatAction.builder().chatId(chatId.toString()).action("typing").build());
        } catch (TelegramApiException e) {
            log.error("Error enviando typing: ", e);
        }
    }
}
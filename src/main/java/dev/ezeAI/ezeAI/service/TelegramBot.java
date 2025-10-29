package dev.ezeAI.ezeAI.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ezeAI.ezeAI.model.Question;
import dev.ezeAI.ezeAI.model.UserState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final AIService aiService;
    private final Map<Long, UserState> userStates = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<Question> questions = new ArrayList<>();

    public TelegramBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            AIService aiService) {
        super(botToken);
        this.botUsername = botUsername;
        this.aiService = aiService;
        loadQuestions();
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            String userName = update.getMessage().getFrom().getFirstName();

            log.info("Mensaje recibido de {}: {}", userName, messageText);

            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText, userName);
            } else {
                handleUserMessage(chatId, messageText, userName);
            }
        }
    }

    private void handleCommand(Long chatId, String command, String userName) {
        String responseText;

        switch (command.toLowerCase()) {
            case "/start":
                responseText = "¡Hola " + userName + "! \uD83D\uDC4B Soy Tux, tu asistente experto en Linux. \uD83D\uDC27\n\n" +
                        "Hoy te voy a ayudar a elegir una distribución Linux perfecta para vos.\n\n" +
                        "¿Cómo querés que te ayude?\n" +
                        "\uD83D\uDD39 Cuestionario: Te hago preguntas específicas y te recomiendo la distro ideal\n" +
                        "\uD83D\uDD39 Preguntas libres: Me preguntás lo que quieras sobre Linux y te respondo\n\n" +
                        "Comandos disponibles:\n" +
                        "/cuestionario - Comenzar cuestionario guiado\n" +
                        "/help - Ver todos los comandos\n" +
                        "/about - Información del bot\n\n" +
                        "¡Podés empezar escribiendo tu pregunta o usando /cuestionario!\n" +
                        "Ejemplo: \"¿Qué distro me recomendás para programar?\"";
                break;

            case "/help":
                responseText = "\uD83E\uDD16 Guía de Comandos - Tux Bot\n\n" +
                        "Comandos principales:\n" +
                        "/start - Mensaje de bienvenida\n" +
                        "/cuestionario - Iniciar cuestionario guiado\n" +
                        "/help - Mostrar esta guía\n" +
                        "/about - Información sobre el bot\n" +
                        "/repo - Ver código fuente\n\n" +
                        "¿Cómo usar el bot?\n" +
                        "• Podés hacer preguntas libres sobre Linux en cualquier momento\n" +
                        "• Durante el cuestionario, también podés hacer preguntas\n" +
                        "• Después de una pregunta, te voy a preguntar si querés continuar el cuestionario\n\n" +
                        "Ejemplos de preguntas:\n" +
                        "- \"¿Cuál es la diferencia entre Ubuntu y Fedora?\"\n" +
                        "- \"¿Qué distro es mejor para gaming?\"\n" +
                        "- \"¿Cómo instalo software en Linux?\"";
                break;

            case "/about":
                responseText = "ℹ️ Acerca de Tux Bot\n\n" +
                        "\uD83D\uDC27 Tux - Asistente Linux con IA\n" +
                        "Especializado en ayudarte a elegir la distribución Linux perfecta.\n\n" +
                        "Tecnologías:\n" +
                        "- Spring Boot 3.5.6\n" +
                        "- Java 21\n" +
                        "- Groq API Model: llama-3.1-8b-instant\n" +
                        "- Telegram Bot API\n\n" +
                        "Funciones:\n" +
                        "- Cuestionario inteligente para recomendaciones\n" +
                        "- Respuestas con IA sobre Linux\n" +
                        "- Modo híbrido: preguntas + cuestionario\n\n" +
                        "Desarrollado por: Ezequiel";
                break;

            case "/repo":
                responseText = "\uD83D\uDC19 ¡Código Abierto!\n\n" +
                        "Este proyecto es open source. ¡Podés ver el código, " +
                        "reportar bugs o contribuir!\n\n" +
                        "➡️ [Ver repositorio en GitHub]\n" +
                        "(El enlace lo agregará Ezequiel)\n\n" +
                        "\uD83D\uDD27 Contribuciones bienvenidas:\n" +
                        "- Mejoras en el cuestionario\n" +
                        "- Nuevas distribuciones\n" +
                        "- Optimizaciones de IA";
                break;

            case "/cuestionario":
                handleQuestionnaireCommand(chatId, userName);
                return;

            default:
                responseText = "❌ Comando no reconocido.\n\n" +
                        "Usa /help para ver los comandos disponibles o simplemente " +
                        "escribime tu pregunta sobre Linux. \uD83D\uDC27";
        }

        sendMessage(chatId, responseText);
    }

    private void loadQuestions() {
        try {
            ClassPathResource resource = new ClassPathResource("preguntas.json");
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    Question[] questionsArray = objectMapper.readValue(jsonContent, Question[].class);
                    questions = List.of(questionsArray);
                    log.info("Preguntas cargadas exitosamente: {} preguntas", questions.size());
                }
            } else {
                log.warn("preguntas.json no encontrado, usando preguntas por defecto");
                questions = createDefaultQuestions();
            }
        } catch (IOException e) {
            log.error("Error cargando preguntas: ", e);
            questions = createDefaultQuestions();
        }
    }

    private List<Question> createDefaultQuestions() {
        List<Question> defaultQuestions = new ArrayList<>();
        // Asumiendo que Question tiene un constructor o builder apropiado
        // Ajusta según tu clase Question
        try {
            defaultQuestions.add(createQuestion("¿Cuál es tu nivel de experiencia con Linux? (principiante/intermedio/avanzado)"));
            defaultQuestions.add(createQuestion("¿Para qué vas a usar principalmente Linux? (trabajo/gaming/programación/uso general)"));
            defaultQuestions.add(createQuestion("¿Qué tan potente es tu computadora? (alta/media/baja potencia)"));
        } catch (Exception e) {
            log.error("Error creando preguntas por defecto: ", e);
        }
        return defaultQuestions;
    }

    // Método auxiliar para crear Question - ajusta según tu implementación
    private Question createQuestion(String preguntaText) {
        Question question = new Question();
        question.setPregunta(preguntaText);
        return question;
    }

    private void handleQuestionnaireCommand(Long chatId, String userName) {
        if (questions.isEmpty()) {
            sendMessage(chatId, "❌ Lo siento, no pude cargar las preguntas del cuestionario. " +
                    "Pero podés hacerme preguntas directamente sobre Linux. \uD83D\uDC27");
            return;
        }

        UserState userState = userStates.get(chatId);
        if (userState == null) {
            userState = new UserState();
            userStates.put(chatId, userState);
        }

        userState.setInQuestionnaire(true);
        userState.setQuestionIndex(0);
        if (userState.getResponses() != null) {
            userState.getResponses().clear();
        }

        String welcomeMessage = "\uD83C\uDFAF Cuestionario Iniciado\n\n" +
                "Te voy a hacer " + questions.size() + " preguntas para recomendarte " +
                "la distribución Linux perfecta para vos.\n\n" +
                "\uD83D\uDCA1 Recordá: En cualquier momento podés hacerme una pregunta " +
                "y después continuamos con el cuestionario.\n\n" +
                "Pregunta 1/" + questions.size() + ":\n" +
                questions.get(0).getPregunta();

        sendMessage(chatId, welcomeMessage);
    }

    private void handleUserMessage(Long chatId, String messageText, String userName) {
        UserState userState = userStates.get(chatId);

        if (userState != null && userState.isInQuestionnaire()) {
            if (aiService.isQuestion(messageText)) {
                handleQuestionDuringQuestionnaire(chatId, messageText, userName, userState);
            } else {
                handleQuestionnaireResponse(chatId, messageText, userName, userState);
            }
        } else {
            handleFreeQuestion(chatId, messageText, userName);
        }
    }

    private void handleQuestionDuringQuestionnaire(Long chatId, String messageText, String userName, UserState userState) {
        try {
            sendTypingAction(chatId);

            log.info("Procesando pregunta durante cuestionario de {}: {}", userName, messageText);

            String context = "El usuario está en medio de un cuestionario para elegir una distribución Linux. " +
                    "Pregunta actual: " + (userState.getQuestionIndex() + 1) + "/" + questions.size() +
                    ". Responde la pregunta de forma concisa y clara.";

            String aiResponse = aiService.generateResponseWithContext(messageText, userName, context);

            // Primero enviar la respuesta a la pregunta
            sendMessage(chatId, aiResponse);

            // Luego un mensaje separado con las opciones
            String continueMessage = "\n\uD83D\uDD04 **¿Qué querés hacer ahora?**\n\n" +
                    "\uD83D\uDCAC **Hacé otra pregunta** - Escribí lo que quieras saber\n" +
                    "\uD83D\uDCDD **Continuar cuestionario** - Respondé la pregunta de abajo\n\n" +
                    "**Pregunta " + (userState.getQuestionIndex() + 1) + "/" + questions.size() + ":**\n" +
                    questions.get(userState.getQuestionIndex()).getPregunta();

            // Enviar después de un pequeño delay
            sendMessage(chatId, continueMessage);

        } catch (Exception e) {
            log.error("Error procesando pregunta durante cuestionario: ", e);
            sendMessage(chatId, "❌ Error procesando tu pregunta. " +
                    "¿Querés continuar con el cuestionario?\n\n" +
                    "**Pregunta " + (userState.getQuestionIndex() + 1) + "/" + questions.size() + ":**\n" +
                    questions.get(userState.getQuestionIndex()).getPregunta());
        }
    }

    private void handleQuestionnaireResponse(Long chatId, String messageText, String userName, UserState userState) {
        int questionIndex = userState.getQuestionIndex();

        if (questionIndex < questions.size()) {
            userState.getResponses().put(questions.get(questionIndex).getPregunta(), messageText);
            userState.setQuestionIndex(questionIndex + 1);

            if (questionIndex + 1 < questions.size()) {
                String nextMessage = "✅ Respuesta guardada.\n\n" +
                        "Pregunta " + (questionIndex + 2) + "/" + questions.size() + ":\n" +
                        questions.get(questionIndex + 1).getPregunta() + "\n\n" +
                        "\uD83D\uDCA1 También podés hacerme preguntas en cualquier momento.";

                sendMessage(chatId, nextMessage);
            } else {
                userState.setInQuestionnaire(false);
                generateFinalRecommendation(chatId, userState.getResponses(), userName);
            }
        }
    }

    private void generateFinalRecommendation(Long chatId, Map<String, String> responses, String userName) {
        try {
            sendTypingAction(chatId);

            String prompt = "Basándote en las siguientes respuestas del cuestionario, " +
                    "recomendá la mejor distribución Linux para este usuario y justificá tu elección:\n\n" +
                    responses.toString();

            String recommendation = aiService.generateResponseWithContext(
                    prompt, userName, "Recomendación final basada en cuestionario completo");

            String finalMessage = "\uD83C\uDF89 ¡Cuestionario Completado!\n\n" +
                    recommendation + "\n\n" +
                    "\uD83D\uDD04 ¿Qué podés hacer ahora?\n" +
                    "• Hacerme más preguntas sobre Linux\n" +
                    "• Usar /cuestionario para empezar de nuevo\n" +
                    "• Usar /help para ver todos los comandos";

            sendMessage(chatId, finalMessage);

        } catch (Exception e) {
            log.error("Error generando recomendación final: ", e);
            sendMessage(chatId, "❌ Error generando recomendación. " +
                    "Podés hacerme preguntas directas sobre las distribuciones que te interesan.");
        }
    }

    private void handleFreeQuestion(Long chatId, String messageText, String userName) {
        try {
            sendTypingAction(chatId);
            String aiResponse = aiService.generateResponse(messageText, userName);

            String fullResponse = aiResponse + "\n\n" +
                    "\uD83D\uDCA1 Tip: Si querés una recomendación personalizada, " +
                    "podés usar /cuestionario para un análisis completo.";

            sendMessage(chatId, fullResponse);
        } catch (Exception e) {
            log.error("Error procesando pregunta libre: ", e);
            sendMessage(chatId, "❌ Lo siento, ocurrió un error al procesar tu pregunta. " +
                    "Por favor, inténtalo de nuevo más tarde.");
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build();

        try {
            execute(message);
            log.info("Mensaje enviado a chat {}", chatId);
        } catch (TelegramApiException e) {
            log.error("Error enviando mensaje: ", e);
        }
    }

    private void sendTypingAction(Long chatId) {
        try {
            org.telegram.telegrambots.meta.api.methods.send.SendChatAction chatAction =
                    org.telegram.telegrambots.meta.api.methods.send.SendChatAction.builder()
                            .chatId(chatId.toString())
                            .action("typing")
                            .build();
            execute(chatAction);
        } catch (TelegramApiException e) {
            log.error("Error enviando acción de typing: ", e);
        }
    }
}
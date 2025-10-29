package dev.ezeAI.ezeAI.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final ChatClient.Builder chatClientBuilder;

    private String preguntasJson = "[]";
    private String distrosJson = "[]";

    @PostConstruct
    public void loadJsonFiles() {
        loadJsonFile("preguntas.json", (content) -> preguntasJson = content);
        loadJsonFile("distros.json", (content) -> distrosJson = content);
    }

    private void loadJsonFile(String fileName, JsonContentSetter setter) {
        try {
            ClassPathResource resource = new ClassPathResource(fileName);
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    setter.setContent(content);
                    log.info("{} cargado exitosamente", fileName);
                }
            } else {
                log.warn("{} no encontrado en classpath, usando array vacío", fileName);
            }
        } catch (IOException e) {
            log.error("Error cargando {}: ", fileName, e);
        }
    }

    @FunctionalInterface
    private interface JsonContentSetter {
        void setContent(String content);
    }

    private static final String SYSTEM_PROMPT = """
            Sos Tux, un asistente experto en distribuciones Linux. 🐧
            Tu tarea es ayudar a los usuarios a elegir la distribución de Linux más adecuada y responder preguntas sobre Linux.
            
            Disponés de la siguiente información:
            
            PREGUNTAS DEL CUESTIONARIO:
            {preguntasJson}
            
            DISTRIBUCIONES DISPONIBLES:
            {distrosJson}
            
            Características de tus respuestas:
            - Sé conciso pero informativo (máximo 2-3 párrafos)
            - Usa un tono amigable y técnico pero fácil de entender
            - Usa emojis ocasionalmente para hacer la conversación más amena
            - Si te preguntan sobre Linux, usa la información de distribuciones disponibles
            - Si no estás seguro de algo, admítelo honestamente
            - Siempre termina sugiriendo si quieren continuar con el cuestionario o hacer más preguntas
            
            Nombre del usuario: {userName}
            Pregunta del usuario: {userMessage}
            """;

    public String generateResponse(String userMessage, String userName) {
        try {
            log.info("Generando respuesta de IA para: {}", userMessage);

            ChatClient chatClient = chatClientBuilder.build();

            PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of(
                    "userName", userName != null ? userName : "Usuario",
                    "userMessage", userMessage,
                    "preguntasJson", preguntasJson,
                    "distrosJson", distrosJson
            ));

            String response = chatClient.prompt(prompt)
                    .call()
                    .content();

            log.info("Respuesta generada exitosamente");
            return response;

        } catch (Exception e) {
            log.error("Error generando respuesta con IA: ", e);
            return "Lo siento, no pude procesar tu pregunta en este momento. " +
                    "Por favor, inténtalo de nuevo más tarde.";
        }
    }

    public String generateResponseWithContext(String userMessage, String userName, String context) {
        try {
            String contextualPrompt = SYSTEM_PROMPT + "\n\nContexto de la conversación: " + context;

            ChatClient chatClient = chatClientBuilder.build();

            PromptTemplate promptTemplate = new PromptTemplate(contextualPrompt);
            Prompt prompt = promptTemplate.create(Map.of(
                    "userName", userName != null ? userName : "Usuario",
                    "userMessage", userMessage,
                    "preguntasJson", preguntasJson,
                    "distrosJson", distrosJson
            ));

            return chatClient.prompt(prompt)
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("Error generando respuesta contextual: ", e);
            return "Lo siento, ocurrió un error procesando tu mensaje.";
        }
    }

    // Método para detectar si el mensaje es una pregunta o una respuesta del cuestionario
    public boolean isQuestion(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String cleanMessage = message.trim().toLowerCase();

        // 1. Si contiene signos de pregunta, ES pregunta

        if (cleanMessage.contains("?") || cleanMessage.contains("¿")) {
            return true;
        }

        // 2. Si empieza con palabras de pregunta, ES pregunta

        String[] questionStarters = {
                "qué", "que", "cómo", "como", "cuál", "cual", "cuándo", "cuando",
                "dónde", "donde", "por qué", "porque", "para qué", "para que",
                "what", "how", "which", "when", "where", "why"
        };

        for (String starter : questionStarters) {
            if (cleanMessage.startsWith(starter + " ")) {
                return true;
            }
        }

        // 3. Si contiene frases de pregunta específicas, ES pregunta
        String[] questionPhrases = {
                "explica", "explícame", "cuéntame", "háblame", "diferencia entre",
                "mejor opción", "recomienda", "me ayuda", "puedes", "podés",
                "qué es", "que es", "cómo es", "como es", "para qué sirve",
                "me podrías", "podrías decir", "necesito saber"
        };

        for (String phrase : questionPhrases) {
            if (cleanMessage.contains(phrase)) {
                return true;
            }
        }

        // 4. IMPORTANTE: Si parece respuesta típica del cuestionario, NO es pregunta

        String[] responseIndicators = {
                "tengo", "uso", "prefiero", "me gusta", "trabajo con", "es para",
                "principalmente", "generalmente", "normalmente", "mi computadora",
                "mi pc", "mi laptop", "mi equipo", "intel", "amd", "nvidia",
                "escritorio", "portátil", "laptop", "notebook", "gamer", "gaming",
                "programación", "desarrollo", "oficina", "estudios", "universidad"
        };

        for (String indicator : responseIndicators) {
            if (cleanMessage.contains(indicator)) {
                return false; // ES respuesta del cuestionario
            }
        }

        // 5. Si es muy corto (1-2 palabras), probablemente es respuesta

        String[] words = cleanMessage.split("\\s+");
        if (words.length <= 2) {
            return false;
        }

        // 6. Por defecto, asumir que NO es pregunta (es respuesta del cuestionario)

        return false;
    }
}
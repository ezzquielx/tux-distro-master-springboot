package dev.ezeAI.ezeAI.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class UserState {
    private boolean inQuestionnaire = false;
    private int questionIndex = 0; // Para rastrear la pregunta actual
    private Map<String, String> responses = new HashMap<>(); // Para almacenar respuestas
}
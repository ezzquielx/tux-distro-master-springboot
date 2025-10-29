FROM openjdk:21-jdk-slim

WORKDIR /app

# Hacer mvnw ejecutable
RUN chmod +x ./mvnw

# Descargar dependencias
RUN ./mvnw dependency:go-offline -B

# Comando para ejecutar la aplicación
CMD ["java", "-jar", "ezeAI-0.0.1-SNAPSHOT.jar"]
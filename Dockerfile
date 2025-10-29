FROM openjdk:21-jdk-slim

WORKDIR /app

# Copiar archivos de configuración de Maven
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .

# Hacer mvnw ejecutable
RUN chmod +x ./mvnw

# Descargar dependencias
RUN ./mvnw dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar aplicación
RUN ./mvnw clean package -DskipTests

# Exponer puerto
EXPOSE 8080

# Comando para ejecutar la aplicación
CMD ["java", "-jar", "ezeAI-0.0.1-SNAPSHOT.jar"]
FROM openjdk:21-jdk-slim

WORKDIR /app

# Copiar archivos de Maven wrapper
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY pom.xml .

# Hacer mvnw ejecutable
RUN chmod +x ./mvnw

# Copiar código fuente
COPY src ./src

# Compilar aplicación (sin go-offline)
RUN ./mvnw clean package -DskipTests -B

# Exponer puerto
EXPOSE 8080

# Comando para ejecutar la aplicación
CMD ["java", "-jar", "target/ezeAI-0.0.1-SNAPSHOT.jar"]
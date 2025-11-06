FROM eclipse-temurin:21-jdk-jammy

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

# Compilar aplicación
RUN ./mvnw clean package -DskipTests -B

# Listar archivos para debugging
RUN ls -la target/

# Exponer puerto
EXPOSE 8080

# Buscar y ejecutar el jar generado (más robusto)
CMD ["sh", "-c", "java -jar target/*.jar"]

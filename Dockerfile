FROM --platform=linux/amd64 eclipse-temurin:18-jre
COPY suzume-0.7.0-SNAPSHOT.jar server.jar
CMD ["java","-jar","server.jar"]
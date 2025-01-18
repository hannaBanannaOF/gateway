FROM registry.access.redhat.com/ubi8/openjdk-21:1.20

COPY build/libs/gateway-1.0.0.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
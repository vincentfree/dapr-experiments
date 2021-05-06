#FROM gradle:7.0-jdk11 as builder
#COPY . .
#RUN gradle :order-backend:shadowJar

FROM adoptopenjdk/openjdk11:jre
#COPY --from
#RUN groupadd --gid 10001 order && useradd --gid order --shell /bin/bash --create-home -u 10001
COPY ./build/libs/order-backend-all.jar ./order-backend.jar
ENTRYPOINT java -jar order-backend.jar

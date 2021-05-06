FROM adoptopenjdk/openjdk11:jre
#COPY --from
#RUN groupadd --gid 10001 order && useradd --gid order --shell /bin/bash --create-home -u 10001
COPY ./build/libs/order-client-all.jar ./order-client.jar
ENTRYPOINT java -jar order-client.jar

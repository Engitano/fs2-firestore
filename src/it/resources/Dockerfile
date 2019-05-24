FROM google/cloud-sdk:229.0.0-alpine

LABEL maintainer "Mark Tranter <mark.tranter@engitano.com>"

# Install Java 8 for Datastore emulator
RUN apk add --update --no-cache openjdk8-jre &&\
    gcloud components install cloud-firestore-emulator beta --quiet

# Volume to persist Datastore data
VOLUME /opt/data

COPY start-firestore .

EXPOSE 8080

ENTRYPOINT ["./start-firestore"]
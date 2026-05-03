FROM container-registry.oracle.com/graalvm/native-image:23 AS build

RUN curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    chmod +x lein && \
    mv lein /usr/bin/lein && \
    lein upgrade

COPY . /usr/src/app

WORKDIR /usr/src/app

RUN sleep 60 && lein do clean, uberjar, native

FROM busybox AS dataset

WORKDIR /resources

COPY resources/vectors.bin.gz vectors.bin.gz
COPY resources/labels.bin.gz labels.bin.gz
COPY resources/ivf.bin.gz ivf.bin.gz

RUN gunzip vectors.bin.gz labels.bin.gz ivf.bin.gz

FROM gcr.io/distroless/base:latest

WORKDIR /app

COPY --from=build /usr/src/app/target/guarita /app/guarita
COPY --from=dataset /resources/vectors.bin /app/resources/vectors.bin
COPY --from=dataset /resources/labels.bin /app/resources/labels.bin
COPY --from=dataset /resources/ivf.bin /app/resources/ivf.bin

CMD ["./guarita"]

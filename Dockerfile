FROM python:3-slim AS dataset

WORKDIR /usr/src/app

COPY scripts/generate_dataset.py scripts/generate_dataset.py

RUN mkdir -p resources && python3 scripts/generate_dataset.py

FROM container-registry.oracle.com/graalvm/native-image:23 AS build

RUN curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    chmod +x lein && \
    mv lein /usr/bin/lein && \
    lein upgrade

COPY . /usr/src/app

WORKDIR /usr/src/app

COPY --from=dataset /usr/src/app/resources/vectors.bin resources/vectors.bin
COPY --from=dataset /usr/src/app/resources/labels.bin resources/labels.bin

RUN lein do clean, uberjar, native

FROM gcr.io/distroless/base:latest

WORKDIR /app

COPY --from=build /usr/src/app/target/guarita /app/guarita
COPY --from=dataset /usr/src/app/resources/vectors.bin /app/resources/vectors.bin
COPY --from=dataset /usr/src/app/resources/labels.bin /app/resources/labels.bin

CMD ["./guarita"]
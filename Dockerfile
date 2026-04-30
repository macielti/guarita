FROM container-registry.oracle.com/graalvm/native-image:23 as build

RUN dnf install -y python3 && \
    curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    chmod +x lein && \
    mv lein /usr/bin/lein && \
    lein upgrade

COPY . /usr/src/app

WORKDIR /usr/src/app

RUN python3 scripts/generate_dataset.py

RUN lein do clean, uberjar, native

FROM gcr.io/distroless/base:latest

WORKDIR /app

COPY --from=build /usr/src/app/target/guarita  /app/guarita

CMD ["./guarita"]

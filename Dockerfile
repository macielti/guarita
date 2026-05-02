FROM python:3-slim AS dataset

WORKDIR /usr/src/app

COPY scripts/generate_dataset.py scripts/generate_dataset.py

RUN pip install --no-cache-dir numpy scikit-learn

RUN mkdir -p resources && python3 scripts/generate_dataset.py

FROM grafana/k6:latest AS k6

FROM container-registry.oracle.com/graalvm/native-image:23 AS build

RUN curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && \
    chmod +x lein && \
    mv lein /usr/bin/lein && \
    lein upgrade

COPY . /usr/src/app

WORKDIR /usr/src/app

COPY --from=dataset /usr/src/app/resources/vectors.bin resources/vectors.bin
COPY --from=dataset /usr/src/app/resources/labels.bin resources/labels.bin
COPY --from=dataset /usr/src/app/resources/ivf.bin resources/ivf.bin
COPY --from=k6 /usr/bin/k6 /usr/bin/k6

RUN lein do clean, uberjar, native-pgo

RUN mkdir -p ./resources/profile-guided-optimizations/ && \
    ./target/guarita -XX:ProfilesDumpFile=./resources/profile-guided-optimizations/profile.iprof & \
    SERVER_PID=$! && \
    for i in $(seq 1 30); do curl -sf http://localhost:9999/ready && break || sleep 2; done && \
    k6 run ./resources/profile-guided-optimizations/test.js && \
    kill $SERVER_PID && \
    wait $SERVER_PID || true

RUN sleep 60 && lein do clean, uberjar, native

FROM gcr.io/distroless/base:latest

WORKDIR /app

COPY --from=build /usr/src/app/target/guarita /app/guarita
COPY --from=dataset /usr/src/app/resources/vectors.bin /app/resources/vectors.bin
COPY --from=dataset /usr/src/app/resources/labels.bin /app/resources/labels.bin
COPY --from=dataset /usr/src/app/resources/ivf.bin /app/resources/ivf.bin

CMD ["./guarita"]

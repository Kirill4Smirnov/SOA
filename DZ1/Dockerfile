# syntax=docker/dockerfile:1
FROM golang:1.23-alpine AS build

WORKDIR /src
COPY go.mod ./
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/feed-service ./cmd/feed-service

FROM alpine:3.21
RUN addgroup -S app && adduser -S -G app app
COPY --from=build /out/feed-service /usr/local/bin/feed-service

USER app
EXPOSE 8080
ENTRYPOINT ["feed-service"]

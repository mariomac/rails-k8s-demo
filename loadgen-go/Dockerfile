FROM golang:1.22-alpine AS build

WORKDIR /build
COPY go.mod ./
COPY main.go ./
RUN CGO_ENABLED=0 go build -o /loadgen .

FROM alpine:3.20

COPY --from=build /loadgen /usr/local/bin/loadgen

CMD ["loadgen"]

# ---- Build stage: lint + test + compile ----------------------------------
FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN apk add --no-cache curl \
 && curl -sSfL https://raw.githubusercontent.com/golangci/golangci-lint/master/install.sh | sh -s -- -b /usr/local/bin \
 && golangci-lint run \
 && go test ./... \
 && CGO_ENABLED=0 go build -trimpath -o /out/api ./cmd/api

# ---- Runtime stage --------------------------------------------------------
FROM gcr.io/distroless/static-debian12
COPY --from=build /out/api /api
EXPOSE 8080
ENTRYPOINT ["/api"]
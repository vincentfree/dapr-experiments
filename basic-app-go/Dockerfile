FROM golang:latest as builder
LABEL maintainer="Vincent Free"
WORKDIR /app
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .

FROM alpine:latest  
RUN apk --no-cache add ca-certificates
WORKDIR /root/
# Copy the Pre-built binary file from the previous stage
COPY --from=builder /app/main .
CMD ["./main"]
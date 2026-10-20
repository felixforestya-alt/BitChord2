# BitChord Listen Together on a Hugging Face Space (Docker, free CPU, no card).
# The Space holds only this file and README.md; the server is built from the repo.
FROM golang:alpine AS build
ENV GOTOOLCHAIN=auto CGO_ENABLED=0
RUN apk add --no-cache git
RUN git clone --depth 1 https://github.com/felixforestya-alt/BitChord2.git /src
WORKDIR /src/backend
RUN go build -o /server .

FROM alpine:3
RUN adduser -D -u 1000 user
COPY --from=build /server /server
USER user
# Spaces route their public https address to this port, through a proxy.
ENV PORT=7860 JAM_TRUST_PROXY=true
EXPOSE 7860
CMD ["/server"]

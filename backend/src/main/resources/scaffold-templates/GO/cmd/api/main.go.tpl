package main

import (
	"log"
	"net/http"
	"os"

	"github.com/{{repoName}}/internal/config"
	"github.com/{{repoName}}/internal/health"
)

func main() {
	cfg := config.Load()

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", health.Handler(cfg))

	addr := ":" + cfg.Port
	log.Printf("{{projectName}} listening on %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatal(err)
	}
}
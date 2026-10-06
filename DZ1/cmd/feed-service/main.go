package main

import (
	"errors"
	"log"
	"net/http"
	"os"
	"time"

	"marketplace/internal/httpapi"
)

func main() {
	addr := ":" + envOrDefault("PORT", "8080")
	server := &http.Server{
		Addr:              addr,
		Handler:           httpapi.NewHandler(),
		ReadHeaderTimeout: 5 * time.Second,
	}

	log.Printf("feed-service is listening on %s", addr)
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

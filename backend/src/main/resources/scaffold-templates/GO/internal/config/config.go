package config

import "os"

// Config holds runtime settings resolved from the environment.
type Config struct {
	Port string
}

func Load() Config {
	return Config{
		Port: getenv("PORT", "8080"),
	}
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
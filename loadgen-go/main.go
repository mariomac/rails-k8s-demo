package main

import (
	"fmt"
	"io"
	"log"
	"math/rand"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

var (
	frontendURL   = envOrDefault("FRONTEND_URL", "http://tapas-frontend:8080")
	minIntervalMs = envIntOrDefault("MIN_INTERVAL_MS", 350)
	maxIntervalMs = envIntOrDefault("MAX_INTERVAL_MS", 500)

	neighborhoods = []string{"El Born", "Poble Sec", "Eixample", "El Raval", "La Barceloneta", "La Boqueria"}
	foodTypes     = []string{"Traditional Tapas", "Seafood Tapas", "Modern Tapas", "Gourmet Tapas", "Basque Tapas"}
	searchTerms   = []string{"bar", "tapas", "cal", "el"}
	names         = []string{"Anna", "Marc", "Laura", "Jordi", "Elena", "David", "Nuria", "Pau", "Cristina", "Sergi"}
	comments      = []string{
		"Great atmosphere and friendly staff.",
		"Would definitely come back for the patatas bravas.",
		"A bit crowded but worth it.",
		"Loved the local wine pairing.",
		"Perfect spot for a quick tapas crawl stop.",
	}

	client = &http.Client{Timeout: 10 * time.Second}
)

func envOrDefault(name, fallback string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return fallback
}

func envIntOrDefault(name string, fallback int) int {
	v := os.Getenv(name)
	if v == "" {
		return fallback
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return n
}

func randomItem(items []string) string {
	return items[rand.Intn(len(items))]
}

func randomInterval() time.Duration {
	spread := maxIntervalMs - minIntervalMs
	ms := minIntervalMs
	if spread > 0 {
		ms += rand.Intn(spread)
	}
	return time.Duration(ms) * time.Millisecond
}

func drain(resp *http.Response) {
	if resp == nil {
		return
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, resp.Body)
}

func browse() error {
	params := url.Values{}
	switch rand.Intn(4) {
	case 1:
		params.Set("q", randomItem(searchTerms))
	case 2:
		params.Set("neighborhood", randomItem(neighborhoods))
	case 3:
		params.Set("food_type", randomItem(foodTypes))
	}

	listResp, err := client.Get(fmt.Sprintf("%s/?%s", frontendURL, params.Encode()))
	if err != nil {
		return err
	}
	drain(listResp)

	restaurantID := 1 + rand.Intn(15)
	detailResp, err := client.Get(fmt.Sprintf("%s/restaurants/%d", frontendURL, restaurantID))
	if err != nil {
		return err
	}
	drain(detailResp)

	if rand.Float64() < 0.2 {
		form := url.Values{
			"author":  {randomItem(names)},
			"rating":  {strconv.Itoa(1 + rand.Intn(5))},
			"comment": {randomItem(comments)},
		}
		reviewResp, err := client.Post(
			fmt.Sprintf("%s/restaurants/%d/reviews", frontendURL, restaurantID),
			"application/x-www-form-urlencoded",
			strings.NewReader(form.Encode()),
		)
		if err != nil {
			return err
		}
		drain(reviewResp)
	}

	return nil
}

func main() {
	log.Printf("Load generator starting against %s", frontendURL)

	for {
		if err := browse(); err != nil {
			log.Printf("Load generator request failed: %v", err)
		}
		time.Sleep(randomInterval())
	}
}

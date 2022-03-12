package hello

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/dapr/go-sdk/client"
	"github.com/rs/zerolog/log"
)

const (
	// helloUrl = "http://127.0.0.1:3500/v1.0/invoke/order-backend/method/hello" //?failure=true
	orderBackend = "order-backend"
	helloMethod  = "hello"
)

var (
	buffer = make(chan []byte, 2)
)

type Client struct {
	HttpClient *http.Client
	DaprClient *client.Client
}

func (c *Client) helloRequest(result chan []byte) error {
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(3)*time.Second)
	daprClient := *c.DaprClient
	resp, err := daprClient.InvokeMethod(ctx, orderBackend, helloMethod, "get")
	if err != nil {
		log.Error().Err(err).Msg("Error in helloRequest")
		log.Info().Msg("Closing request...")
		close(result)
		cancel()
		return err
	}
	result <- resp
	cancel()
	return nil
}

func (c *Client) HelloTimerTask(d time.Duration) {

	tick := time.NewTicker(d)
	for {
		select {
		case <-tick.C:
			go func(b chan []byte) {
				err := c.helloRequest(b)
				if err != nil {
					defer handleRequestPanic()
					fmt.Println("Closing application...")
					log.Panic().Err(err).Msg("Fatal error in helloRequest")
				}
			}(buffer)
		case resp := <-buffer:
			log.Info().Str("message", string(resp)).Msg("message has been send")
		}
	}
}

func (c *Client) closeClient() {
	dapr := *c.DaprClient
	dapr.Close()
}

func handleRequestPanic() {
	if r := recover(); r != nil {
		fmt.Println("Recovering from panic:", r)
	}
}

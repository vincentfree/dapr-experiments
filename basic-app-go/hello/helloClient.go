package hello

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/dapr/go-sdk/client"
)

const (
	// helloUrl = "http://127.0.0.1:3500/v1.0/invoke/order-backend/method/hello" //?failure=true
	orderBackend = "order-backend"
	helloMethod  = "hello"
)

var (
	buffer = make(chan []byte, 2)
	logger = log.Default()
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
		log.Println(err.Error())
		fmt.Println("Closing request...")
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
			go func() {
				err := c.helloRequest(buffer)
				if err != nil {
					defer handleRequestPanic()
					fmt.Println("Closing application...")
					//c.closeClient()
					logger.Panicln("Fatal error in helloRequest |", err.Error())
				}
			}()
		case resp := <-buffer:
			logger.Printf("The message: %s\n", string(resp))
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

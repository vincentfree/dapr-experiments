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
)

var(
	buffer chan[]byte = make(chan []byte, 2)
	logger = log.Default()
	localClient Client
)

type Client struct {
	HttpClient *http.Client
	DaprClient *client.Client
}

func (c *Client) helloRequest(result chan []byte) error {
	ctx := context.Background()
	
	daprClient, err := client.NewClient()
	if err != nil {
		log.Fatalln("unable to create a dapr client")
	}

	client := *localClient.DaprClient
	resp, err := daprClient.InvokeMethod(ctx, "order-backend", "hello", "get")
	if err != nil {
		log.Println(err.Error())
		fmt.Println("Closing application...")
		close(result)
		client.Close()
		return err
	}
	result <- resp
	return nil
}

func setupLocalClient(c *Client) {
	localClient.HttpClient = c.HttpClient
	daprClient, err := client.NewClient()
	if err != nil {
		log.Fatalln("unable to create a dapr client")
	}
	localClient.DaprClient = &daprClient
}

func (c *Client) HelloTimerTask(d time.Duration) {
	setupLocalClient(c)

	tick := time.NewTicker(d)
	for {
		select {
		case <-tick.C:
			go c.helloRequest(buffer)
		case resp := <-buffer:
			logger.Printf("The message: %s\n",string(resp))
		}
	}
}

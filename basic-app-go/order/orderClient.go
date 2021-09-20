package order

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math/rand"
	"time"

	"github.com/dapr/go-sdk/client"
	"github.com/google/uuid"
)

var (
	words = [7]string{
		"uppercutting",
		"appreciation",
		"cyclothymias",
		"mismanagements",
		"parthenogenetic",
		"pottle",
		"maras",
	}
	msgChannel = make(chan string)
	logger     = log.Default()
)

type DaprClient struct {
	Dapr client.Client
}

type Order struct {
	Id      string `json:"id"`
	Name    string `json:"name"`
	OrderId string `json:"order_id"`
}

func (o *Order) toJson() ([]byte, error) {
	json, err := json.Marshal(o)
	if err != nil {
		return nil, err
	}
	return json, nil

}

func (o *Order) ToJsonString() string {
	order, err := o.toJson()
	if err != nil {
		return "{\"message\": \"Failed to decode json\"}"
	}
	return string(order)
}

func (d *DaprClient) request() {
	ctx := context.Background()
	word := words[rand.Intn(len(words))]
	uuid, _ := uuid.NewRandom()
	o := Order{Id: word, Name: word, OrderId: uuid.String()}
	b, _ := o.toJson()
	err := d.Dapr.PublishEvent(ctx, "pubsub", "order.events", b)
	if err != nil {
		fmt.Printf("Failed to send order event: %s to kafka\n", string(b))
	}
	msgChannel <-fmt.Sprintf("the order message: %s, has been send", o.ToJsonString())
}

func (c *DaprClient) OrderTimerTask(d time.Duration) {
	tick := time.NewTicker(d)
	for {
		select {
		case <-tick.C:
			go c.request()
		case resp := <-msgChannel:
			logger.Printf("The published message: %s\n", resp)
		}
	}
}

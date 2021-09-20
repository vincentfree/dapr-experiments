package main

import (
	"fmt"
	"net/http"
	"time"

	"com.github.vincentfree/basic-app/hello"
	"com.github.vincentfree/basic-app/order"
	"github.com/dapr/go-sdk/client"
)

func main() {
	fmt.Println("Starting application...")
	daprClient, err := client.NewClient()
	if err != nil {
		panic(err.Error())
	}

	d := order.DaprClient{Dapr: daprClient}
	client := hello.Client{HttpClient: http.DefaultClient}
	go d.OrderTimerTask(3 * time.Second)
	go client.HelloTimerTask(3 * time.Second)
	select {}
}

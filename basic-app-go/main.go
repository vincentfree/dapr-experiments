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
	helloClient := hello.Client{HttpClient: http.DefaultClient}
	go d.OrderTimerTask(3 * time.Second)
	go helloClient.HelloTimerTask(3 * time.Second)
	select {}
}

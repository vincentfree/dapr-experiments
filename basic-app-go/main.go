package main

import (
	"fmt"
	"net/http"
	"time"

	"com.github.vincentfree/basic-app/hello"
)

func main() {
	fmt.Println("Starting application...")
	// daprClient, err := client.NewClient()
	// if err != nil {
	// 	log.Fatalln("unable to create a dapr client")
	// }
	client := hello.Client{HttpClient: http.DefaultClient}
	// client := hello.Client{HttpClient: http.DefaultClient, DaprClient: &daprClient}
	client.HelloTimerTask(3 * time.Second)
	select {}
}

package main

import (
	"context"
	"fmt"
	"github.com/dapr/go-sdk/client"
	"log"
	"net/http"
	"os"
	"os/signal"
	"time"
)

var (
	ctx = context.Background()
)

const (
	stateStore = "statestore"
)

func main() {
	signalCh := make(chan os.Signal, 1)
	signal.Notify(signalCh)

	fmt.Println("Starting Product API...")
	timeout,cancel := context.WithTimeout(ctx, time.Duration(10)*time.Second)
	defer cancel()
	dc, err := client.NewClient()
	if err != nil {
		panic(err)
	}
	productApi := Api{dc}
	productApi.GetProductsHandler()
	//defer shutDownApp(dc)
	go awaitSignal(signalCh,timeout, dc)
	log.Fatal(http.ListenAndServe(":8081", nil))
}

func shutDownApp(d client.Client, txt ...string) {
	shutdownCtx, cancel := context.WithTimeout(ctx, time.Duration(5)*time.Second)
	log.Println("Shutting down Dapr Client...",txt[0])
	err := d.Shutdown(shutdownCtx)
	if err != nil {
		panic(err)
	}
	cancel()
	os.Exit(0)
}

func awaitSignal(sigCh chan os.Signal,ctx context.Context, daprClient client.Client) {
	for {
		select {
		case <- ctx.Done():
			shutDownApp(daprClient,"Ran into timeout")
		case <-sigCh:
			shutDownApp(daprClient)
		}
	}
}

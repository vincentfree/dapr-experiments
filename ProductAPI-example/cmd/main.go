package main

import (
	"context"
	"fmt"
	"github.com/dapr/go-sdk/client"
	"log"
	"net/http"
	"time"
)

var (
	ctx = context.Background()
)

const (
	stateStore = "statestore"
)

func main() {
	fmt.Println("Starting Product API...")
	//http.HandleFunc("/products/", func(w http.ResponseWriter, r *http.Request) {
	//	fmt.Println(" the path param value is: ",r.URL.Path[len("/products/"):])
	//})

	dc, err := client.NewClient()
	if err != nil {
		panic(err)
	}
	productApi := Api{dc}
	productApi.GetProductsHandler()
	log.Fatal(http.ListenAndServe(":8081", nil))
	//defer shutDownApp(dc)

}

func shutDownApp(d client.Client) {
	shutdownCtx, cancel := context.WithTimeout(ctx, time.Duration(5)*time.Second)
	err := d.Shutdown(shutdownCtx)
	if err != nil {
		panic(err)
	}
	cancel()
}

package main

import (
	"context"
	"errors"
	"fmt"
	"github.com/dapr/go-sdk/client"
	"github.com/golang/protobuf/proto"
	"github.com/vincentfree/dapr-experiments/product-service-example/pkg/workshop"
	"log"
	"net/http"
	"time"
)

type Api struct {
	client.Client
}

func (api Api) GetProductsHandler() {
	http.HandleFunc("/products/", api.productRouter)
}

func (api Api) productRouter(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		api.getProductByID(w, r)
	case http.MethodPost:
		api.storeNewProduct(w, r)
	case http.MethodDelete:
		api.deleteProduct(w, r)

	default:
		w.WriteHeader(400)
		_, _ = fmt.Fprintln(w, "Unsupported Http method")
	}
}

func (api Api) getProductByID(w http.ResponseWriter, r *http.Request) {
	timeoutCtx, cancel := context.WithTimeout(r.Context(), time.Duration(2)*time.Second)
	id := r.URL.Path[len("/products/"):]
	product, err := api.getProduct(timeoutCtx, stateStore, id)
	if err != nil {
		log.Println(err)
		w.WriteHeader(400)
		_, _ = fmt.Fprintln(w, err)
		cancel()
		return
	}

	fmt.Println("Product:", product)
	_, err = w.Write([]byte(product.String()))
	if err != nil {
		cancel()
		return
	}
	cancel()
}

func (api Api) getProduct(ctx context.Context, stateStore string, key string) (*workshop.Product, error) {
	state, err := api.GetState(ctx, stateStore, key)
	if err != nil {
		return nil, err
	}
	product := &workshop.Product{}
	if len(state.Value) > 0 {
		err = proto.Unmarshal(state.Value, product)
		if err != nil {
			return nil, err
		}
		return product, nil
	} else {
		return nil, errors.New("could not find a product with key: " + key)
	}
}

func (api Api) storeNewProduct(w http.ResponseWriter, r *http.Request) {
	if len(r.URL.Path) > len("/products/") {
		id := r.URL.Path[len("/products/"):]
		item := workshop.Product{
			ProductId: id,
			Name:      "dummy",
			Quantity:  5,
		}
		out, err := proto.Marshal(&item)
		if err != nil {
			log.Fatalln("failed to encode Product", err)
		}
		err = api.SaveState(r.Context(), stateStore, id, out)
		if err != nil {
			return
		}
	} else {
		_, _ = fmt.Fprintln(w, "Pass a path parameter value for the product")
	}

}

func (api Api) deleteProduct(w http.ResponseWriter, r *http.Request) {
	if len(r.URL.Path) > len("/products/") {
		id := r.URL.Path[len("/products/"):]
		err := api.DeleteState(r.Context(), stateStore, id)
		if err != nil {
			log.Println("failed to delete product")
			_, _ = fmt.Fprintln(w, "Failed to delete product:", id)
			return
		} else {
			_, _ = fmt.Fprintln(w, id, "is deleted")
		}
	}
}

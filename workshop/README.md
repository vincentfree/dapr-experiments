# Workshop Dapr

**D**istributed **ap**plication **r**untime (DAPR)

> Simplify cloud-native application development
> Focus on your application’s core logic and keep your code simple and portable

## preconditions

1. You need to have docker installed to be able to run Dapr in standalone mode.
2. For Kubernetes support you'll of course need kubernetes installed locally or in a cloud environment.

## What will we build

### Product API

The Product API should be able to do the following

* get a product description
* add new product item
* subtract product items, products can't go below 0 so this should be verified

### Order API

The Order API should be able to do the following

* create an order based on products
* place an order
* delete an order

### Order Handler service

This service should be able to

* listen to orders coming in
* handle the order moving through different states
* re-inject items when an order gets canceled

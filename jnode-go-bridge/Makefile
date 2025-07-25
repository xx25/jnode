# Makefile
.PHONY: build test clean install

BINARY_NAME=jnode-binkp
BUILD_DIR=build
MAIN_PACKAGE=./cmd/jnode-binkp

build:
	mkdir -p $(BUILD_DIR)
	go build -o $(BUILD_DIR)/$(BINARY_NAME) $(MAIN_PACKAGE)

build-linux:
	mkdir -p $(BUILD_DIR)
	GOOS=linux GOARCH=amd64 go build -o $(BUILD_DIR)/$(BINARY_NAME)-linux-amd64 $(MAIN_PACKAGE)

build-windows:
	mkdir -p $(BUILD_DIR)
	GOOS=windows GOARCH=amd64 go build -o $(BUILD_DIR)/$(BINARY_NAME)-windows-amd64.exe $(MAIN_PACKAGE)

test:
	go test -v ./...

clean:
	rm -rf $(BUILD_DIR)

install: build
	cp $(BUILD_DIR)/$(BINARY_NAME) /usr/local/bin/

docker-build:
	docker build -t jnode-binkp .

docker-run:
	docker run -d --name jnode-binkp \
		-p 24554:24554 \
		-v /path/to/jnode.conf:/app/jnode.conf \
		-v /path/to/inbound:/app/inbound \
		-v /path/to/outbound:/app/outbound \
		jnode-binkp -server -config /app/jnode.conf
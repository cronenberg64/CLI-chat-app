#!/bin/bash

# Compile if needed
if [ ! -f "ChatClient.class" ]; then
    echo "Compiling..."
    javac ChatClient.java
fi

# Run client
HOST=${1:-localhost}
PORT=${2:-6667}
echo "Connecting to $HOST:$PORT..."
java ChatClient $HOST $PORT

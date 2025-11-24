#!/bin/bash

# Compile if needed
if [ ! -f "ChatServer.class" ]; then
    echo "Compiling..."
    javac ChatServer.java ClientHandler.java
fi

# Run server
PORT=${1:-6667}
echo "Starting server on port $PORT..."
java ChatServer $PORT

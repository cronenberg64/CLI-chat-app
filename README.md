# CLI Chat App

A network systems final project implementing a command line-based chat application with TCP sockets, concurrent server handling, and real-time messaging capabilities using java.

## Features
- Direct messaging between users
- File exchange capabilities  
- Channel-based group messaging
- Multi-threaded server architecture
- Line-based command protocol

## Team Members
- Jonathan Setiawan
- Kawata Ryota
- Soncillan Nika Kristin Esclamado

## Project Structure
```
cli-chat-app/
├── src/
│   ├── ChatServer.java
│   ├── ChatClient.java
│   └── ClientHandler.java
├── Makefile
├── run-server.sh
├── run-client.sh
├── PROTOCOL.md
├── QUICKSTART.md
└── README.md
```


## Getting Started
*Details to be added as development progresses*

## Work Division

Jonathan: Server Core

TCP socket setup and server initialization
Multi-threaded client handling
Basic message broadcasting
Server main loop and connection management

Ryota: Client Application

Client socket connection logic
User interface and input handling
Message display formatting
Client command parsing

Nika: Protocol & Features

Message protocol design and parsing
File transfer implementation
Channel system (optional feature)
Error handling and protocol states
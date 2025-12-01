# CLI Chat App

A network systems final project implementing a command line-based chat application with TCP sockets, concurrent server handling, and real-time messaging capabilities using java.

## Features
- **Direct messaging** between users (1-on-1 private messages)
- **File exchange** capabilities
- **Channel-based group messaging** (multi-user channels)
- **Auto-discovery** on local WiFi network (UDP broadcast)
- **Multi-threaded server** architecture
- **IRC-inspired protocol** (line-based commands)

## Team Members
- Jonathan Setiawan
- Kawata Ryota
- guy 

## Project Structure
cli-chat-app/
├── src/
│ ├── server/
│ ├── client/
│ └── common/
├── docs/
├── tests/
└── README.md


## Getting Started

### Automatic Connection (Same WiFi)
When you run the client without arguments, it will automatically discover any server running on the same WiFi network:

```bash
java ChatClient
```

The client will:
1. Listen for server broadcasts on the local network (5 second timeout)
2. Automatically connect to the first server found
3. Fall back to localhost:6667 if no server is discovered

### Manual Connection
You can still specify a server manually:

```bash
java ChatClient <host> [port]
java ChatClient 192.168.1.100 6667
```

### Starting the Server
```bash
java ChatServer [port]
java ChatServer 6667
```

The server will:
- Listen for client connections on the specified port (default: 6667)
- Broadcast its presence on UDP port 6666 every 3 seconds
- Allow automatic discovery by clients on the same network

## Work Division

Person 1: Server Core

TCP socket setup and server initialization
Multi-threaded client handling
Basic message broadcasting
Server main loop and connection management

Person 2: Client Application

Client socket connection logic
User interface and input handling
Message display formatting
Client command parsing

Person 3: Protocol & Features

Message protocol design and parsing
File transfer implementation
Channel system (optional feature)
Error handling and protocol states
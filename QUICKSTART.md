# Quick Start Guide

## Prerequisites
- Java JDK 8 or higher installed
- Terminal/Command Prompt

## Quick Test (3 Steps)

### Step 1: Start the Server

```bash
java ChatServer
```

Or use the convenience script:
```bash
./run-server.sh
```

You should see:
```
[SERVER] Started on port 6667
[SERVER] Waiting for connections...
```

### Step 2: Connect First Client (Client 1)

Open a **new terminal window** and run:

```bash
java ChatClient
```

Or use the convenience script:
```bash
./run-client.sh
```

In the client, type these commands:
```
/nick <your nickname>
/join #general
/chan #general Hello!
```

### Step 3: Connect Second Client (Client 2)

Open **another new terminal window** and run:

```bash
java ChatClient
```

Or use the convenience script:
```bash
./run-client.sh
```

In this client, type:
```
/nick <your nickname>
/join #general
/chan #general Hello!
```

Now you should see the messages exchanged between Client 1 and Client 2.

## Testing File Transfer

In Client 1's terminal:
```
/file client2 test-file.txt
```

Client 2 should automatically receive the file as `received_test-file.txt`.

## Testing Direct Messages

In Client 1's terminal:
```
/msg client2 This is a private message
```

Only Client 2 will see this message.

## Common Commands

| What you want to do | Command |
|---------------------|---------|
| Set your name | `/nick <nickname>` |
| Join a channel | `/join <#channel>` |
| Leave a channel | `/part <#channel>` |
| Send to channel | `/chan <#channel> <message>` |
| Private message | `/msg <user> <message>` |
| List channels | `/list` |
| List users | `/users [#channel]` |
| Send file | `/file <user> <filepath>` |
| Challenge to game | `/game challenge <user>` |
| Accept game | `/game accept <user>` |
| Place ship (Game) | `/place <coord> <H/V>` |
| Fire shot (Game) | `/fire <coord>` |
| Surrender (Game) | `/surrender` |
| Disconnect | `/quit [message]` |
| Show help | `/help` |

## Compilation Options

### Using Makefile (recommended)
```bash
make compile    # Compile all files
make server     # Run server
make client     # Run client
make clean      # Remove compiled files
make help       # Show all options
```

### Using Scripts
```bash
./run-server.sh [port]          # Run server (default port: 6667)
./run-client.sh [host] [port]   # Run client (default: localhost:6667)
```

### Manual Compilation
```bash
javac ChatServer.java ClientHandler.java ChatClient.java
java ChatServer [port]
java ChatClient [host] [port]
```

## Multiple Clients Test

To test with multiple clients:

1. Start the server once
2. Open 2-3 terminal windows
3. Run `java ChatClient` in each window
4. Set different nicknames in each (`/nick client1`, `/nick client2`, etc.)
5. All join the same channel: `/join #general`
6. Send messages and test the features

## Connecting from Another Computer (LAN)

To connect from another laptop on the same Wi-Fi:

1.  **Find the Host IP**:
    On the computer running the server, run:
    ```bash
    ifconfig | grep "inet " | grep -v 127.0.0.1
    ```
    Look for an IP like `192.168.x.x` or `172.x.x.x`.
    The IP address is going to be used for the host address and should be next to the `inet` keyword.

2.  **Prepare the Client Laptop**:
    You need to copy the following files from the host to the client laptop:
    - Note: it won't be necessary if the client also git cloned the repository
    - `bin/` folder (contains compiled code)
    - `chat.jks` (required for SSL connection)

3.  **Run the Client**:
    On the second laptop, you can use the Makefile for convenience:
    ```bash
    make client-connect
    # Enter server host: <your host IP address>
    # Enter server port: <your host port address> (default: 6667)
    ```
    
    Or run manually:
    ```bash
    java -cp bin ChatClient <HOST_IP> <HOST_PORT>
    ```
    Replace `<HOST_IP>` with the IP you found in step 1.

    *Note: Ensure your firewall allows connections on port 6667.*

## Architecture Overview

```
Terminal 1: Server
    ├── Listens on port 6667
    └── Spawns thread for each client

Terminal 2: Client (Client 1)
    ├── Connects to server
    ├── Input thread: reads user commands
    └── Receiver thread: displays messages

Terminal 3: Client (Client 2)
    ├── Connects to server
    ├── Input thread: reads user commands
    └── Receiver thread: displays messages
```

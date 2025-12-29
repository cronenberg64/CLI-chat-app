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

### Step 2: Connect First Client (Alice)

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
/nick alice
/join #general
/chan #general Hello everyone!
```

### Step 3: Connect Second Client (Bob)

Open **another new terminal window** and run:

```bash
java ChatClient
```

In this client, type:
```
/nick bob
/join #general
/chan #general Hi Alice!
```

Now you should see the messages exchanged between Alice and Bob!

## Testing File Transfer

In Alice's terminal:
```
/file bob test-file.txt
```

Bob should automatically receive the file as `received_test-file.txt`.

## Testing Direct Messages

In Alice's terminal:
```
/msg bob This is a private message
```

Only Bob will see this message.

## Common Commands

| What you want to do | Command |
|---------------------|---------|
| Set your name | `/nick yourname` |
| Join a channel | `/join #channelname` |
| Send to channel | `/chan #channelname your message` |
| Private message | `/msg username your message` |
| Send a file | `/file username filepath` |
| See all users | `/users` |
| See all channels | `/list` |
| Leave a channel | `/part #channelname` |
| Disconnect | `/quit` |
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

## Troubleshooting

**Q: Server won't start - "Address already in use"**
- A: Port 6667 is in use. Try: `java ChatServer 6668`

**Q: Client can't connect**
- A: Make sure the server is running first
- A: Check if you're using the correct host and port

**Q: Commands don't work**
- A: Make sure you set your nickname first with `/nick yourname`
- A: All commands must start with `/`

**Q: Can't send to channel**
- A: You must join the channel first with `/join #channelname`

## Multiple Clients Test

To test with multiple clients:

1. Start the server once
2. Open 3-5 terminal windows
3. Run `java ChatClient` in each window
4. Set different nicknames in each (`/nick alice`, `/nick bob`, etc.)
5. All join the same channel: `/join #test`
6. Send messages and watch them broadcast to everyone!

## Connecting from Another Computer (LAN)

To connect from another laptop on the same Wi-Fi:

1.  **Find the Host IP**:
    On the computer running the server, run:
    ```bash
    ifconfig | grep "inet " | grep -v 127.0.0.1
    ```
    Look for an IP like `192.168.x.x` or `172.x.x.x`.

2.  **Prepare the Client Laptop**:
    You need to copy the following files from the host to the client laptop:
    - `bin/` folder (contains compiled code)
    - `chat.jks` (required for SSL connection)

3.  **Run the Client**:
    On the second laptop, you can use the Makefile if you have it:
    ```bash
    make client-connect
    # Enter server host: <your host IP address>
    # Enter server port: <your host port address>
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

Terminal 2: Client (Alice)
    ├── Connects to server
    ├── Input thread: reads user commands
    └── Receiver thread: displays messages

Terminal 3: Client (Bob)
    ├── Connects to server
    ├── Input thread: reads user commands
    └── Receiver thread: displays messages
```

## Next Steps

- Read `README.md` for complete documentation
- Read `PROTOCOL.md` for protocol details
- Try testing with 5-10 simultaneous clients
- Test file transfers with different file types
- Experiment with multiple channels

Enjoy your chat application!

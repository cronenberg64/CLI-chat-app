# Technical Summary - CLI Chat Application

## Project Overview
A command-line chat application implementing an IRC-inspired protocol with automatic server discovery on local WiFi networks.

## Mandatory Requirements ✅

### 1. Exchange of Text Messages Between Users
- **Implementation**: Direct messaging via `/msg` command
- **Location**: `ClientHandler.java:161-179` (handleMsg method)
- **Protocol**: `MSG <user> <message>`
- **How it works**: Messages are routed directly from sender to recipient without broadcasting

### 2. Exchange of Files Between Users
- **Implementation**: File transfer via `/file` command
- **Location**:
  - Client sending: `ChatClient.java:311-339` (sendFile method)
  - Server routing: `ClientHandler.java:225-280` (handleFile method)
  - Client receiving: `ChatClient.java:169-200` (receiveFile method)
- **Protocol**: `FILE <user> <filename> <size>` followed by binary data
- **How it works**: Binary file data is sent after text-based header

### 3. 1-on-1 Direct Messages
- **Implementation**: Same as requirement #1 (MSG command)
- **Privacy**: Messages are only sent to the specified recipient, not broadcast

### 4. Channel-based Messaging (Optional - Implemented)
- **Implementation**: Multi-user channels via `/join` and `/chan` commands
- **Location**:
  - Join: `ClientHandler.java:131-144` (handleJoin method)
  - Channel message: `ClientHandler.java:181-198` (handleChan method)
- **Protocol**: `JOIN #channel`, `CHAN #channel <message>`
- **How it works**: Server maintains channel membership lists and broadcasts messages to all channel members

### 5. Same WiFi Auto-Connection
- **Implementation**: UDP broadcast discovery system
- **Location**:
  - Server broadcast: `ChatServer.java:62-93` (startDiscoveryBroadcast method)
  - Client discovery: `ChatClient.java:28-59` (discoverServer method)
- **How it works**:
  - Server broadcasts "CHAT_SERVER:IP:PORT" on UDP port 6666 every 3 seconds
  - Client listens for broadcast when started without arguments
  - Automatic connection to first discovered server
  - Falls back to localhost if no server found

## Technical Requirements ✅

### 1. TCP Sockets
- **Server**: `ChatServer.java:30` - ServerSocket for accepting connections
- **Client**: `ChatClient.java:63` - Socket for connecting to server
- **Each connection**: Handled via individual Socket instances in ClientHandler

### 2. Parsing Line-Based Commands
- **Client side**: `ChatClient.java:210-309` (processCommand method)
- **Server side**: `ClientHandler.java:50-93` (processCommand method)
- **Format**: All commands are newline-terminated (`\n`)
- **Examples**: `NICK alice\n`, `JOIN #general\n`, `MSG bob hello\n`

### 3. Server Concurrency (Threads)
- **Implementation**: One thread per client connection
- **Location**: `ChatServer.java:37-38`
  ```java
  ClientHandler handler = new ClientHandler(clientSocket, this);
  Thread thread = new Thread(handler);
  thread.start();
  ```
- **Discovery thread**: `ChatServer.java:91-92` (daemon thread for UDP broadcast)
- **Client receiver thread**: `ChatClient.java:72-74` (daemon thread for receiving messages)

### 4. Broadcast vs. Direct Message Routing
- **Direct messages**: `ClientHandler.java:161-179` - sent only to specified user
- **Channel broadcast**: `ChatServer.java:113-125` (broadcastToChannel method)
- **Server-wide broadcast**: `ChatServer.java:154-161` (broadcastQuit method)
- **Logic**: Server checks command type and routes accordingly

### 5. Error Handling and Protocol States
- **Authentication state**: `ClientHandler.java:14,61-62` - clients must set nickname first
- **Error codes**:
  - 400: Bad request (invalid format)
  - 401: Not authenticated
  - 404: User/channel not found
  - 409: Nickname conflict
  - 500: Server error
- **Error responses**: `ClientHandler.java` - all error cases send ERROR messages
- **Examples**:
  - Duplicate nickname: `ChatServer.java:112-114`
  - User not found: `ClientHandler.java:172-175`
  - Not in channel: `ClientHandler.java:191-194`

### 6. Text-Based Protocol (IRC-inspired)
- **Documentation**: `PROTOCOL.md`
- **Inspiration**: Lightweight IRC (Internet Relay Chat) protocol
- **Commands**: NICK, JOIN, PART, MSG, CHAN, LIST, USERS, FILE, QUIT
- **Responses**: WELCOME, OK, ERROR, MSG, CHAN, JOIN, PART, USERLIST, CHANLIST
- **Compatibility**: Uses standard IRC conventions (# for channels, line-based format)

## Architecture

### Components
1. **ChatServer**: Main server class
   - Accepts TCP connections on port 6667
   - Spawns ClientHandler threads
   - Manages client and channel registries
   - Broadcasts UDP discovery packets

2. **ClientHandler**: Per-client connection handler
   - Runs in separate thread
   - Processes commands from client
   - Maintains authentication state
   - Routes messages to other clients

3. **ChatClient**: Client application
   - Discovers servers via UDP
   - Connects via TCP
   - Separate thread for receiving messages
   - Command-line interface

### Communication Flow

#### Connection Establishment
```
1. Server broadcasts "CHAT_SERVER:192.168.1.100:6667" via UDP
2. Client receives broadcast and extracts server address
3. Client connects to server via TCP
4. Server spawns ClientHandler thread
5. Server sends WELCOME message
```

#### Message Routing
```
Direct Message:
Client A → Server (MSG bob hello) → Server routes → Client B receives

Channel Message:
Client A → Server (CHAN #general hi) → Server broadcasts → All clients in #general receive
```

#### File Transfer
```
1. Sender: FILE bob test.txt 1024
2. Server validates recipient exists
3. Server sends FILEOFFER to recipient
4. Sender transmits binary data
5. Server buffers and forwards to recipient
6. Recipient saves as received_test.txt
```

## Data Structures

### Server-Side
- `Map<String, ClientHandler> clients` - Nickname to handler mapping (ConcurrentHashMap)
- `Map<String, Set<String>> channels` - Channel to members mapping (ConcurrentHashMap)

### Synchronization
- All client/channel registration methods are `synchronized`
- Thread-safe collections (ConcurrentHashMap) prevent race conditions
- Each ClientHandler operates independently on its socket

## Testing

### Same WiFi Test
1. Start server: `java ChatServer`
2. On same network, start client: `java ChatClient` (no arguments)
3. Client should auto-discover and connect

### Multi-Client Test
1. Start one server
2. Start 3-5 clients (auto-discovery or manual)
3. All clients join #general: `/join #general`
4. Test channel broadcasting
5. Test direct messages between pairs
6. Test file transfers

### Protocol Compliance
- All commands follow PROTOCOL.md specification
- Error codes match IRC conventions
- Channel names require # prefix
- Usernames validated (1-20 alphanumeric)

## Performance Characteristics

- **Scalability**: One thread per client (suitable for small to medium deployments)
- **Network efficiency**: UDP broadcast for discovery, TCP for reliable messaging
- **Discovery interval**: 3 seconds (configurable)
- **Discovery timeout**: 5 seconds client-side
- **Concurrency**: Thread-safe collections prevent blocking
- **File transfer**: Binary mode, direct routing (no intermediate storage)

## Security Considerations

- **Broadcast scope**: Limited to local network (255.255.255.255)
- **No authentication**: Simple nickname-based system (suitable for trusted LAN)
- **No encryption**: Plain text protocol (consider for future enhancement)
- **Input validation**: Nickname format, channel naming, command syntax

## Code Statistics

- **Total Files**: 3 Java files (Server, ClientHandler, Client)
- **Server Lines**: ~220 lines
- **ClientHandler Lines**: ~330 lines
- **Client Lines**: ~445 lines
- **Protocol Commands**: 8 client commands, 10 server responses
- **Threads**: Minimum 3 per client (main, discovery, receiver)

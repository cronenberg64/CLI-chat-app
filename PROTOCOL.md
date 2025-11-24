# Chat Application Protocol Specification

## Overview
A lightweight IRC-inspired text-based protocol for command-line chat.

## Connection Flow
1. Client connects to server via TCP
2. Server sends welcome message
3. Client must send NICK command to set username
4. Client can then join channels, send messages, transfer files

## Protocol Commands

### Client to Server

| Command | Format | Description | Example |
|---------|--------|-------------|---------|
| NICK | `NICK <nickname>` | Set/change username | `NICK alice` |
| JOIN | `JOIN <channel>` | Join a channel | `JOIN #general` |
| PART | `PART <channel>` | Leave a channel | `PART #general` |
| MSG | `MSG <user> <message>` | Direct message to user | `MSG bob Hello!` |
| CHAN | `CHAN <channel> <message>` | Send message to channel | `CHAN #general Hi everyone` |
| LIST | `LIST` | List all channels | `LIST` |
| USERS | `USERS [channel]` | List users (all or in channel) | `USERS #general` |
| FILE | `FILE <user> <filename> <size>` | Initiate file transfer | `FILE bob test.txt 1024` |
| FILEDATA | `FILEDATA <size>\n<binary>` | Send file data | Internal use |
| QUIT | `QUIT [message]` | Disconnect from server | `QUIT Goodbye` |

### Server to Client

| Response | Format | Description |
|----------|--------|-------------|
| WELCOME | `WELCOME <message>` | Connection established |
| OK | `OK <command> <message>` | Command successful |
| ERROR | `ERROR <code> <message>` | Command failed |
| MSG | `MSG <from> <message>` | Direct message received |
| CHAN | `CHAN <channel> <from> <message>` | Channel message received |
| JOIN | `JOIN <channel> <user>` | User joined channel |
| PART | `PART <channel> <user>` | User left channel |
| USERLIST | `USERLIST <channel> <users...>` | List of users |
| CHANLIST | `CHANLIST <channels...>` | List of channels |
| FILEOFFER | `FILEOFFER <from> <filename> <size>` | File transfer offer |
| FILEDATA | `FILEDATA <size>\n<binary>` | File data |
| QUIT | `QUIT <user> <message>` | User disconnected |

## Error Codes
- 400: Bad request (invalid command format)
- 401: Not authenticated (NICK required)
- 404: User/channel not found
- 409: Nickname already in use
- 500: Server error

## Protocol Rules
1. All text commands are UTF-8 encoded, line-terminated with `\n`
2. File transfers use binary mode after size header
3. Channel names must start with `#`
4. Usernames are case-insensitive, 1-20 alphanumeric characters
5. Maximum message length: 512 bytes

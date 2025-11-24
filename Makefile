# Makefile for Chat Application

# Java compiler
JC = javac
# Java runtime
JVM = java

# Source files
SOURCES = ChatServer.java ClientHandler.java ChatClient.java

# Class files
CLASSES = ChatServer.class ClientHandler.class ChatClient.class

# Default target
all: compile

# Compile all Java files
compile: $(SOURCES)
	$(JC) $(SOURCES)
	@echo "Compilation successful!"

# Run server
server: compile
	$(JVM) ChatServer

# Run server on custom port
server-port: compile
	@read -p "Enter port number [6667]: " port; \
	port=$${port:-6667}; \
	$(JVM) ChatServer $$port

# Run client
client: compile
	$(JVM) ChatClient

# Run client with custom host/port
client-connect: compile
	@read -p "Enter server host [localhost]: " host; \
	read -p "Enter server port [6667]: " port; \
	host=$${host:-localhost}; \
	port=$${port:-6667}; \
	$(JVM) ChatClient $$host $$port

# Clean compiled files
clean:
	rm -f *.class

# Clean and recompile
rebuild: clean compile

# Help
help:
	@echo "Available targets:"
	@echo "  make compile        - Compile all Java files"
	@echo "  make server         - Run server on default port (6667)"
	@echo "  make server-port    - Run server on custom port"
	@echo "  make client         - Run client (localhost:6667)"
	@echo "  make client-connect - Run client with custom host/port"
	@echo "  make clean          - Remove compiled class files"
	@echo "  make rebuild        - Clean and recompile"
	@echo "  make help           - Show this help message"

.PHONY: all compile server server-port client client-connect clean rebuild help

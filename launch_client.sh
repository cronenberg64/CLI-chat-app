#!/bin/bash

# Get the absolute path of the current directory
PROJECT_DIR=$(pwd)

echo "Launching Chat Client in a new Terminal window..."

osascript <<EOF
tell application "Terminal"
    do script "cd \"$PROJECT_DIR\" && make client"
    set the bounds of the front window to {100, 100, 1100, 900} -- x, y, width, height
    activate
end tell
EOF

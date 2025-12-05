Project Topic
• Implement a command line-based chat application
• A server application
• Setup on a ‘central’ computer, to wait for and process user’s requests
• A client application
• used by different users to connect to the server and communicate with
each other.

Project Functional Requirements
• Mandatory:
• Exchange of text messages between users
• Exchange of files between users
• users can exchange message 1-on-1 (direct messages)
• Optional
• Have a list of channels on a server
• users can send message to a channel for all currently connected users to
that channel to see.

Project Technical Requirements
• TCP sockets
• Parsing line-based commands
• Server concurrency (threads)
• Broadcast vs. direct message routing
• Error handling and protocol states

Project Recommended Features
• Implement a text-based protocol
• Preferably a real standard protocol (A lightweight version of IRC for
example)

Deliverables
• Source code for your client and server applications
• A technical report of your project (<= 8 pages)
• Introduction: specify your understanding of your problem and the scope
of what you are trying to solve (1/2)
• Related work: find report and articles on existing chat applications and
the technical protocols they use and report on them(1)
• Method: The analysis and design of your solution (use UML diagrams)
with explanations (4-5)
• Discussion: describe your implemented solution including strong and
weak point (1-2)
• Conclusion: summarize your work (1/2)
# MinecraftSimpleClient
A very simple client for communicating with a Minecraft server

The used version of Minecraft is 1.12


All packets received from the server are forwareded to the corresponding event. Every event is identified after `packetId` and `packetState`.


Events can be declared in the MainClass and added to the bus of other events by using `addEvent()` function.
The program provided already hooks the chat mechanism and prints all messages. Thus it can register a user by also sending the `/register pass pass` comment and the logging in with `/login`.

The Client also allows for programmed events so that a genuine user's actions can be simulated. For example the provided example clicks the first slot from the hotbar and open the game menu. It does so until it reaches the `bedwars` game and enters it.

All details of the protocol used are found here: https://wiki.vg/index.php?title=Protocol&oldid=14204#Click_Window

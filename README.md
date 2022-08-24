# UWA_RouteApp
2020s1 cits3002 final project.



## Test Environment
The system is :   Windows Subsystem for Linux (WSL)
The linux no:      Ubuntu 20.04 LTS




## The general idea of find trip is:

First step:    find a valid trip from origin to destination
	  Such as "1;Find;Destination;Station1;Station1's udp port"

Second step:  pass back this valid trip to the original station
	     Sunch as "1;Route;Destination;Station1;Station1's udp port"

Third step:  Start to find the final time arriving the destination
	  Such as "1;Reroute;Destination;Station1;Station1's udp port;arriving_time"

Forth step:  Get the arriving time adn pass back to the origin
	  Such as "1;Get;Destination;Station1;Station1's udp port;arriving_time"

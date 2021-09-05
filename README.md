# xnor-server
[![GitHub license](https://img.shields.io/github/license/jacekpoz/xnor-lib)](https://github.com/jacekpoz/xnor-lib/blob/master/LICENSE)
[![Data definitely not sold](https://img.shields.io/badge/data-definitely%20not%20sold-red)](https://img.shields.io/badge/data-definitely%20not%20sold-red)
[![Don't look at DataStealer.java](https://img.shields.io/badge/don't%20look%20at-DataStealer.java-red)](https://img.shields.io/badge/don't%20look%20at-DataStealer.java-red)

server for the xnor chat app I'm making
not that big but got some big plans

the script for creating the database is also included here since I didn't know where to put it

## Prerequisites
[Java 16](https://openjdk.java.net/projects/jdk/16/) (I compiled it on AdoptOpenJDK-16.0.1+9 which I recommend, but any version should work probably)

[MySQL Server](https://dev.mysql.com/downloads/mysql/) (I tested it on version 8.0.26 but Idk which versions would work just use the newest one I guess)

a working computer could be useful, I tested it on ubuntu-21.04 and again it should work on like ubuntu server or windows server or whatever it's just that I haven't tested it there, if you have please post some screenshots of it working on your machine or something and I'll include it here

## Installation
ok so first you download java and set it up you can get an installer [here](https://adoptopenjdk.net/) which will make it easier

then the next step is to get mysql I already put the website up there just click it and download it you could also get [MySQL Workbench](https://dev.mysql.com/downloads/workbench/) but it's kinda buggy and doesn't have a dark theme so it's up to you really, you can set it up without the workbench

now that you have both java and mysql on your machine you wanna somehow run the createdatabase.sql file I don't really remember how to do that but if you're running your own server you should be able to figure it out

check if the database is set up correctly and if all the tables are there and then create a user with the name `xnor-chat-client` and grant it all permissions on the database, I guess I should add the user creation and the permission grantage (yep I made that word up but it sounds cool) to the file but ehh you can figure it out for now

and I guess I shouldn't really hardcode the user name in the server buuuuuut it's not that high of a priority now

ok ummmm I think that's it you should have everything set up by now

## Usage
by now you should have java installed and mysql and the database all set up so if you want to run the server all you need to do is `java -jar xnor-server-x.x.x.jar <port-number>` with `<port-number>` being obviously whichever port you wanna use for the xnor server so like 2137 or something

don't close the terminal or powershell or whatever you are using cause it will turn off the server maybe some day I'll make a gui for it but right now it's all you have so just get used to it for now

the server will print a message whenever a user turns off their client or something and that's it for now but it displays a lot of shit like the current logged in user and which chat they're in and uhhh it's not really that much but still pretty cool right?? I'll add some more stuff to that later but now the main focus is to finish the client and get the chat running

## Contributing
if you think you can do this better than me just make an issue or do a pull request or whatever I'll look into it

## License
[GPL-3.0](https://choosealicense.com/licenses/gpl-3.0/)

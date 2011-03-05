test: playsound
	./playsound '/Applications/Mail.app/Contents/Resources/Mail Sent.aiff'
playsound: playsound.m Makefile
	gcc -o $@ -Wall -Os -framework Cocoa $<


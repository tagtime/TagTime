#!/bin/csh

foreach i (*.wav *.WAV)
	echo $i
	./playsound $i
	sleep 1
	./playsound $i
	sleep 1
	./playsound $i
	sleep 3
end

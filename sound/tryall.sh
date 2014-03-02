#!/bin/csh

foreach i (*.wav *.WAV)
  echo $i
  afplay $i
  sleep 1
  afplay $i
  sleep 1
  afplay $i
  sleep 3
end


# version from dan goldstein that works in bash on linux:
#!/bin/bash
#filetypes=(wav WAV)
#for type in ${filetypes[@]} 
#do
#  for i in $(ls *.$type)
#  do
#    echo $i
#    playsound $i  # playsound is built in on linux
#    sleep 1
#  done
#done


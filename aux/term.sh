#!/bin/bash

osascript -e "
tell application \"Terminal\"
  tell window 1
    do script \"sleep 5\"
    set background color to \"red\"
    set cursor color to \"blue\"
    set custom title to \"TagTime\"
    repeat while busy
      delay 1
    end repeat
    close
  end tell
end tell"


#  tell window 1
#    do script \"$*; exit\"
#    set background color to \"red\"
#    set cursor color to \"blue\"
#    set custom title to \"TagTime\"
#    set win_id to id
#  end tell
#
#  set w_ids to (id of every window)
#  
#  repeat while w_ids contains win_id
#    delay 1
#    set w_ids to (id of every window)
#  end repeat

# EXAMPLE APPLESCRIPT:
# set RGBGreen to {0, 10000, 0} as RGB color
# set RGBRed to {10000, 0, 0} as RGB color
# set RGBBlue to {0, 0, 10000} as RGB color
# set RGBBlack to {0, 0, 0} as RGB color
# set RGBWhite to {65535, 65535, 65535} as RGB color
# 
# set RGBcolors to {RGBGreen, RGBRed, RGBBlue, RGBBlack}
# 
# repeat with curColor in RGBcolors
  # tell application "Terminal"
    # activate
    # with timeout of 1800 seconds
      # do script with command "pwd"
      # tell window 1
        # set background color to curColor
        # set cursor color to RGBGreen
        # if curColor = {65535, 65535, 65535} then
          # set normal text color to RGBGreen
        # else
          # set normal text color to RGBWhite
        # end if
        # set bold text color to "red"
        # set title displays shell path to true
        # set title displays window size to true
        # set title displays device name to true
        # set title displays file name to true
        # set number of columns to 120
        # set number of rows to 40
      # end tell
    # end timeout
  # end tell
# end repeat


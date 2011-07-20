#!/bin/bash

xyz="testxyz"
return=`/usr/bin/osascript << EOT
tell app "System Events"
  Activate
  display dialog "" buttons {"OK"} default button 1 default answer "" with title "TagTime Test Title $xyz" 
  set pingans to text returned of the result
end tell
EOT`
echo "[$return]"

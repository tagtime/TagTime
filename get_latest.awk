{if($1 > max) { max = $1; latest = FILENAME}} END { print latest }

#!/usr/bin/env ruby
# Crude command line interface to the old Beeminder API.
# This is deprecated! See beemapi.pl for the new Beeminder API.

require 'net/http'

$base = "http://localhost:3000"     # have this one second for testing locally
$base = "http://beta.beeminder.com"  # beta.beeminder is the non-ssl version

def beemapi(cmd, origin, usr, graph, data=nil)
  retries = 10
  res = nil

  begin
    surl = "#{$base}/#{usr}/goals/#{graph}/datapoints/#{cmd}"
    url = URI.parse(surl)
    http = Net::HTTP.new(url.host, url.port)
    http.read_timeout = 8640
    http.start{|http|
      req = Net::HTTP::Post.new(url.path)
      req.set_form_data({"datapoints_text"=>data, "origin"=>origin})
      res = http.request(req)
    }
  rescue StandardError, Timeout::Error
    p res
    print "DEBUG: retrying #{cmd} in 10 seconds...\n"
    sleep 10
    retry if (retries -= 1) > 0
  end
  
  return res.nil? ? nil : res.body
end

if ARGV.length < 3
  print "USAGE: #{__FILE__} COMMAND ORIGIN USR GRAPH < datapoints\n"
  print "  (COMMAND is one of create_all, tagtime_update, query)\n"
  exit(1)
end


cmd,origin,usr,graph = ARGV

case cmd
when "create_all", "tagtime_update"
  beemapi(cmd, origin, usr, graph, STDIN.read)
when "query"
  print "query not supported yet\n"
else
  print "No such beemapi command: #{cmd}\n"
end

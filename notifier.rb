#!/usr/bin/env ruby

require 'rubygems'
gem 'xmpp4r-simple'

require 'xmpp4r-simple'

# Change @jabber and @notify as appropriate
@jabber = { :username => 'taskbot.notifier@gmail.com',
            :password => 'insecure' }

@notify = ["david.g.yang@gmail.com", "dreeves@gmail.com", "bsoule@gmail.com"]

im = Jabber::Simple.new(@jabber[:username], @jabber[:password])

Signal.trap(:TERM) { exit; }

s = STDIN.read

@notify.each { |notify| 
	im.deliver(notify, s)
	sleep 0.2
} 

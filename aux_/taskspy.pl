#!/usr/bin/perl
# Run this to watch someone's task file who is sharing it.
# (Also shares your own -- uploads it to yootles.com whenever it changes.)
# Specify their tagtime username on the command line.

require "$ENV{HOME}/.tagtimerc";
require "${path}util.pl";

if(scalar(@ARGV)==0) { 
  die "USAGE: $0 <tagtime username of someone sharing their task file>\n"; 
}

my $host = "yootles.com";
my $you = shift;  # username of the person you're watching

my @targets = ("$usr.tsk", "$you.tsk");

print "Watching $usr.tsk and uploading to $host whenever it changes...\n";
print "Watching $you.tsk on $host and downloading whenever it changes...\n"; 

# kludge alert -- just copying in code from watching.pl

# initialize the %last hash for last mod time of each file.
for my $t (@targets) {
  ($dev,$ino,$mode,$nlink,$uid,$gid,$rdev,$size,
   $atime,$mtime,$ctime,$blksize,$blocks) = stat($t);
  $last{$t} = $mtime;
}

my $i = 1;
while(1) {
  system("rsync -t ${host}:/var/www/html/yootles/outbox/$you.tsk $you.tsk");
  if($i % (45*60) == 0) { print "."; }

  for my $t (@targets) {
    ($dev,$ino,$mode,$nlink,$uid,$gid,$rdev,$size,
     $atime,$mtime,$ctime,$blksize,$blocks) = stat($t);

    if ($mtime != $last{$t}) {
      print "\nCHANGE DETECTED TO $t\n";
      if($t eq "$usr.tsk") { 
        system("scp $t ${host}:/var/www/html/yootles/outbox/$t"); 
      } else {
        system("$XT -T \"TaskSpy $t\" " . 
               "-fg DeepPink -bg black -cr green -bc -rw -e vim $t");
      }
      $last{$t} = $mtime;
    }
  }
  sleep(int(rand()*5+5));
  $i++;
}


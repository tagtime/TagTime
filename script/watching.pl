#!/usr/bin/perl
# Run some commands whenever any of a set of files changes (see USAGE below).
# Example:
# ./watching.pl foo.txt bar.txt do scp foo.txt remote.com:. and cat bar.txt
# To only do something to the file that changed, refer to it as {}.

$| = 1;  # autoflush

my $p = position("do", @ARGV); # position of 1st occurrence of "do" in @ARGV.
if (@ARGV < 3 || $p == -1 || !($p >= 1 && $p < $#ARGV)) {
 die "USAGE: watching FILE+ do COMMAND [ARGS] (and COMMAND [ARGS])*\n";
}

my $cmdstr = join(' ', splice(@ARGV, $p+1));  # grab stuff after the "do"
my @cmds = split(/\s+and\s+/, $cmdstr);
pop(@ARGV);  # remove the "do" on the end.
my @targets = @ARGV;
print "Watching {", join(' ', @targets), "} do (", join('; ', @cmds), "):\n";

# initialize the %last hash for last mod time of each file.
for my $t (@targets) {
  ($dev,$ino,$mode,$nlink,$uid,$gid,$rdev,$size,
   $atime,$mtime,$ctime,$blksize,$blocks) = stat($t);
  $last{$t} = $mtime;
}

my $i = 1;
while(1) {
  if($i % (45*60) == 0) { print "."; }

  for my $t (@targets) {
    ($dev,$ino,$mode,$nlink,$uid,$gid,$rdev,$size,
     $atime,$mtime,$ctime,$blksize,$blocks) = stat($t);

    if ($mtime != $last{$t}) {
      print "\nCHANGE DETECTED TO $t\n";
      for (@cmds) { my $tmp = $_; $tmp =~ s/\{\}/$t/g; system($tmp); }
      $last{$t} = $mtime;
    }
  }
  sleep(1);
  $i++;
}


# Call like so: position($element, @list).
sub position {
  my $x = shift;
  if(@_==0) { return -1; }
  if($x eq $_[0]) { return 0; }
  shift;
  my $p = position($x,@_);
  if($p==-1) { return -1; }
  return 1+$p;
}

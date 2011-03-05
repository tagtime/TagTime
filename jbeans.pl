#!/usr/bin/env perl
# Run this in the background and do "touch jgulp" for each jelly bean you eat.
# You get to eat a jelly bean each time one of the target files changes.

$| = 1;  # autoflush

if (@ARGV < 1) { 
  print <<'USAGE';
USAGE: ./jbeans.pl FILE+ &
(Run in the background and do 'touch jgulp' each time you eat a jelly bean.)
USAGE
  exit;
}

my @targets = @ARGV;
push(@targets, "jgulp");
print "Watching {", join(' ', @targets), "}: \n";

# initialize the %last hash for last mod time of each file.
for my $t (@targets) {
  ($dev,$ino,$mode,$nlink,$uid,$gid,$rdev,$size,
   $atime,$mtime,$ctime,$blksize,$blocks) = stat($t);
  $last{$t} = $mtime;
}

my $mayeat = 0;
my $i = 1;
while(1) {
  if($i % (45*60) == 0) { print "."; }

  for my $t (@targets) {
    ($dev,$ino,$mode,$nlink,$uid,$gid,$rdev,$size,
     $atime,$mtime,$ctime,$blksize,$blocks) = stat($t);

    if ($mtime != $last{$t}) {
      if($t eq "jgulp") { $mayeat--; }
      else { $mayeat++; }
      print "\nCHANGE DETECTED TO $t.  Jelly beans you may eat: $mayeat.  ";
      $last{$t} = $mtime;
    }
  }
  sleep(1);
  $i++;
}

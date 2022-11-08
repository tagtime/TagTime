#!/usr/bin/env perl
my @argflags = [];
while(substr($ARGV[0], 0, 1) eq '-') {
    $flag = shift @ARGV;
    if(grep(/^$flag$/, @argflags)) {
        $val = shift @ARGV;
    }
    print("Ignored flag $flag $val\n");
}
my ($src, $dest) = @ARGV;
print "Would have copied from $src to $dest\n";

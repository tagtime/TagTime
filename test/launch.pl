#!/usr/bin/env perl
use File::Basename;

my $assertions_passed = 0;

END {
    print("$assertions_passed assertions passed!\n");
}

sub assert_numeq {
    my ($expected, $actual) = @_;
    assert($expected == $actual, "Expected $expected, got $actual");
}

sub assert {
    my ($condition, $msg) = @_;
    if($condition) {
        $assertions_passed++;
        return;
    }
    if (!$msg) {
        my ($pkg, $file, $line) = caller(0);
        open my $fh, "<", $file;
        my @lines = <$fh>;
        close $fh;
        $msg = "$file:$line: " . $lines[$line - 1];
    }
    die "Assertion failed: $msg";
}

$launchTime = 1592693841; # Birth of this test suite

# Override home to be our test dir
$ENV{HOME} = "./" . dirname(__FILE__);
eval {
    require "$ENV{HOME}/.tagtimerc";
};
if ($@) {
    die "$0: $ENV{HOME}/.tagtimerc cannot be loaded ($!). Do you need to run install.py?\n"
}

require "${path}util.pl";

my $lstping = prevping($launchTime);
my $nxtping = nextping($lstping);

assert_numeq(1592691017, $lstping);
assert_numeq(1592696838, $nxtping);

$newlog = "$path/test/logs/nonexistent.log";
unlink $newlog;
$logf = "$path/test/logs/nonexistent.log";

$cmd = "${path}launch.pl";        # Catch up on any old pings.
system($cmd) == 0 or print "SYSERR: $cmd\n";

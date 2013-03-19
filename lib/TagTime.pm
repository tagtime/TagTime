package TagTime;
use strict;
use warnings;

use base qw(Exporter);
our @EXPORT_OK = qw(match);

=head2 match($expr, $line)

Returns whether the boolean tag expression is true for the given line 
from a log file (assume it's pre-stripped).

=cut

sub match {
  my($expr, $line) = @_;
  my %h;

  return 1 if $expr =~ /^\s*\(?\s*\)?\s*$/;

  for(split(/\s+/, $line)) { $h{$_} = 1; }

  # TODO: Refactor this so we're not using eval() at the end.
  # Based upon what the user has entered, almost anything could happen!

  $expr =~ s/([^\|])\|([^\|])/$1\|\|$2/g;
  $expr =~ s/([^\&])\&([^\&])/$1\&\&$2/g;
  $expr =~ s/([\w-]+)/\$h{'$1'}/g;

  return eval($expr);
}

1;

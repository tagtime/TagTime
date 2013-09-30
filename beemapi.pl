# Rough implementation of some Beeminder API calls needed for TagTime
# See http://beeminder.com/api

# Get your personal Beeminder auth token (after signing in) from
#   https://www.beeminder.com/api/v1/auth_token.json
# And set a global variable like $beemauth = "abc123";
# (That's already done in TagTime settings but if you're using this elsewhere
# you'll need to set $beemauth.)

use LWP::UserAgent;  # tip: run 'sudo cpan' and at the cpan prompt do 'upgrade'
use JSON;            # then 'install LWP::UserAgent' and 'install JSON' etc
use HTTP::Request::Common;  # pjf recomends cpanmin.us
use Data::Dumper; $Data::Dumper::Terse = 1;
$beembase = 'https://www.beeminder.com/api/v1/';

# Delete datapoint with given id for beeminder.com/u/g
sub beemdelete { my($u, $g, $id) = @_;
  my $ua = LWP::UserAgent->new;
  my $uri = $beembase . 
            "users/$u/goals/$g/datapoints/$id.json?auth_token=$beemauth";
  my $resp = $ua->delete($uri);
  beemerr('DELETE', $uri, {}, $resp);
}

# Fetch all the datapoints for beeminder.com/u/g
sub beemfetch { my($u, $g) = @_;
  my $ua = LWP::UserAgent->new;
  #$ua->timeout(30); # give up if no response for this many seconds; default 180
  my $uri = $beembase .
            "users/$u/goals/$g/datapoints.json?auth_token=$beemauth";
  my $resp = $ua->get($uri);
  beemerr('GET', $uri, {}, $resp);
  return decode_json($resp->content);
}

# Create a new datapoint {timestamp t, value v, comment c} for bmndr.com/u/g
# and return the id of the new datapoint.
sub beemcreate { my($u, $g, $t, $v, $c) = @_;
  my $ua = LWP::UserAgent->new;
  my $uri = $beembase."users/$u/goals/$g/datapoints.json?auth_token=$beemauth";
  my $data = { timestamp => $t,
               value     => $v,
               comment   => $c };
  my $resp = $ua->post($uri, Content => $data);
  beemerr('POST', $uri, $data, $resp);
  my $x = decode_json($resp->content);
  return $x->{"id"};
}

# Update a datapoint with the given id. Similar to beemcreate/beemdelete.
sub beemupdate { my($u, $g, $id, $t, $v, $c) = @_;
  my $ua = LWP::UserAgent->new;
  my $uri = $beembase . 
            "users/$u/goals/$g/datapoints/$id.json?auth_token=$beemauth";
  my $data = { timestamp => $t,
               value     => $v,
               comment   => $c };
  # you'd think the following would work:
  # my $resp = $ua->put($uri, Content => $data);
  # but it doesn't so we use the following workaround, courtesy of
  # http://stackoverflow.com/questions/11202123/how-can-i-make-a-http-put
  my $req = POST($uri, Content => $data);
  $req->method('PUT');
  my $resp = $ua->request($req);
  beemerr('PUT', $uri, $data, $resp);
}

# Takes request type (GET, POST, etc), uri string, hashref of data arguments, 
# and response object; barfs verbosely if problems. 
# Obviously this isn't the best way to do this.
sub beemerr { my($rt, $uri, $data, $resp) = @_; 
  if(!$resp->is_success) {
    print "Error making the following $rt request to Beeminder:\n$uri\n";
    print Dumper $data;
    print $resp->status_line, "\n", $resp->content, "\n";
    exit 1;
  }
}

1; # when requiring a library in perl it has to return 1.


# How Paul Fenwick does it in Perl:
#my ($user, $auth_token, $datapoint, $comment);  
#my $mech = WWW::Mechanize( autocheck => 1 )
#$mech->post(
#"http://beeminder.com/api/v1/users/$busr/goals/$slug/datapoints.json?
#auth_token=$auth_token",
#{
#  timestamp => time(),
#  value => $datapoint,
#  comment => $comment
#}
#);

#import <Cocoa/Cocoa.h>

@interface Delegate : NSObject { }
@end

@implementation Delegate
- (void) sound: (NSSound *) sound didFinishPlaying: (BOOL) aBool
{
  [[NSApplication sharedApplication] terminate: nil];
}
@end


int main (int argc, char *argv[])
{
  if(argc!=2) {
    NSLog(@"usage: %s cowbell.wav", argv[0]);
    exit(1);
  }

  [[NSAutoreleasePool alloc] init];
  
  NSSound *sound = [[NSSound alloc]
		     initWithContentsOfFile: 
		       [NSString stringWithCString: argv[1]]
		     byReference: YES];
  
  [sound setDelegate: [[[Delegate alloc] init] autorelease]];
  [sound play];

  [[NSRunLoop currentRunLoop] run];

  return 1; // failed
}


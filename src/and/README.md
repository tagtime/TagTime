# Getting Started

To build the android app you first need to download and install Android Studio.  You can get that from http://developer.android.com/sdk

You should now be able to run Android Studio. Do it.

Now you need to configure a target.  You can do this by clicking "SDK Manager" in the "Tools" menu.  Select Android 4.3, which is API Level 18.

After that it will sit there with a progress bar going
through all the things it needs to download.

To run it on your phone you first need to enable usb debugging.  If
you're running something older than 4.0 it's under
`Settings > Applications > Development`.  If newer it's in `Settings >
Developer Options`.  If on 4.2 or newer it's a hidden setting until you
first go to `Settings > About Phone` and tap `Build Number` seven
times.  Really.

With usb debugging enabled, plug the phone into your computer.  You
should see a notification on the phone like "Android debugging
enabled".

Now return to Android Studio, and we can run the app on the phone for testing, by clicking "Run" in the "Run" menu.

If you already have TagTime installed on your phone it won't work:

    Failure [INSTALL_FAILED_ALREADY_EXISTS]

You need to back up your Tagtime data and uninstall Tagtime first,
and then click "Run" again.

Note: You will need to disable Instant Run to compile TagTime. You can do that by going to Android Studio settings and disabling everything in "Build, Execution, Deployment / Instant Run".
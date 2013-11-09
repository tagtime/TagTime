# Getting Started

To build the android app you first need to download and unpack the
android sdk.  You can get that from http://developer.android.com/sdk

You should now have a directory `sdk/tools` which contains an `android`
binary.  Add `sdk/tools` to your path:

    $ export PATH="$PATH:/path/to/sdk/tools"

Now you need to configure a target.  To list available targets run:

    $ android list targets

By default this should have only one target, and it's probably for a
more recent version of Android than your test device.  Open up the SDK
Manager so you can install an older target:

    $ android

This should open up a GUI where you can download the files for an older
version of Android.  `src/and/project.properties` has `target=android-4`
so check the box next to "Android 1.6 (API 4)" and click the "Install
Packages" button.

After you accept the license it will sit there with a progress bar going
through all the things it needs to download.  When it finishes quit the
SDK Manager.

Listing targets again you should now see one available like:

    $ android list targets
    Available Android targets:
    ----------
    id: 1 or "android-4"
         Name: Android 1.6
    ...

Now we can configure our project to use this target:

    $ cd TagTime/src/and
    $ android update project --target android-4 --path .

This makes lots of changes, but the important one is that it creates
`build.xml` which allows you to run `ant`, which is kind of like `make`:

    $ ant debug
    [ lots of text ]
    BUILD SUCCESSFUL

To run it on your phone you first need to enable usb debugging.  If
you're running something older than 4.0 it's under
`Settings > Applications > Development`.  If newer it's in `Settings >
Developer Options`.  If on 4.2 or newer it's a hidden setting until you
first go to `Settings > About Phone` and tap `Build Number` seven
times.  Really.

With usb debugging enabled, plug the phone into your computer.  You
should see a notification on the phone like "Android debugging
enabled".

We now need to run `adb` which is in `platform-tools` and not `tools`,
so let's add it to the path:

    $ export PATH="$PATH:/path/to/sdk/platform-tools"

Now we can run the app on the phone for testing:

    $ adb -d install bin/TPController-debug.apk

If you already have TagTime installed on your phone it won't work:

    Failure [INSTALL_FAILED_ALREADY_EXISTS]

You need to back up your Tagtime data and uninstall Tagtime first,
and then run the `adb` command again:

    $ adb -d install bin/TPController-debug.apk
    Success

Now you can go into Apps on your phone and start TagTime.

Note: You will need the latest version of ActionBarSherlock to compile
TagTime after recent updates.
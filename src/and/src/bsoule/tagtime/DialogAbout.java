/*
 * Copyright (C) 2012 Uluc Saranli
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package bsoule.tagtime;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Implements a simple Dialog to display general information about the Beeminder application, as
 * well as the current version, revision and build information. The TextView in the layout converts
 * web addresses into links for its components.
 */
public class DialogAbout extends AlertDialog {
    private static final String TAG = "AboutDialog";

    public DialogAbout(final Context context, int theme) {
        super( context, theme );

        Context appctx = TagTime.getAppContext();

        setTitle( appctx.getText( R.string.about_title ) );
        setCancelable( true );
        LayoutInflater inflater = getLayoutInflater();
        View newview = inflater.inflate( R.layout.about_dialog, null );

        TextView versionTxt = (TextView) newview.findViewById( R.id.about_version );
        String vtext = appctx.getText( R.string.about_version ).toString();
        try {
            String pkgname = appctx.getPackageName();
            String version = appctx.getPackageManager().getPackageInfo( pkgname, 0 ).versionName;
            int vcode = appctx.getPackageManager().getPackageInfo( pkgname, 0 ).versionCode;
            versionTxt.setText( vtext.replace( "vvv", version ).replace( "bbb",
                    Integer.toString( vcode ) ) );
        } catch (PackageManager.NameNotFoundException e) {
            Log.w( TAG,
                    "getPackageInfo() failed. This should not have happened! Msg:" + e.getMessage() );
            versionTxt.setText( vtext.replace( "vvv", "???" ) );
        }

        Button rate = (Button) newview.findViewById( R.id.about_rate );
        rate.setOnClickListener( new View.OnClickListener() {
            public void onClick( View view ) {
                context.startActivity( new Intent( Intent.ACTION_VIEW, Uri
                        .parse( "market://details?id=" + TagTime.APP_PNAME ) ) );
                dismiss();
            }
        } );
        setView( newview );
        //setIcon( R.drawable.tagtime_03);

        setButton( BUTTON_POSITIVE, TagTime.getAppContext().getText(R.string.about_dismiss), (new OnClickListener() {
            public void onClick( DialogInterface dialog, int which ) {
                dismiss();
            }
        }) );
//
//        setButton( BUTTON_NEUTRAL, TagTime.getAppContext().getText(R.string.about_changelog), (new OnClickListener() {
//            public void onClick( DialogInterface dialog, int which ) {
//                AlertDialog changelog = new DialogChangeLog( context );
//                changelog.show();
//                dismiss();
//            }
//        }) );
    }
}

package com.orbotix.att.command;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import com.att.android.speech.ATTSpeechActivity;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotProvider;
import orbotix.view.connection.SpheroConnectionView;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class CommandActivity extends Activity implements SpeechWebServiceCall.SpeechCallCompletedListener {

    private static final int REQUEST_SPEECH_INTERACTION = 1337;
    private static final String SPEECH_URL = "https://api.att.com/rest/2/SpeechToText";

    private SpheroConnectionView mConnectionView;
    private View mConnectionLayout;
    private Robot mRobot;
    private Button mSpeakButton;
    private String mOAuthToken;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mSpeakButton = (Button)findViewById(R.id.speak_button);
        setupConnectionView();
    }

    private void setupConnectionView() {
        mConnectionLayout = findViewById(R.id.connection_layout);
        mConnectionView = (SpheroConnectionView)findViewById(R.id.connection_view);
        mConnectionView.setSingleSpheroMode(true);
        //mConnectionView.setRowBackgroundDrawable(getResources().getDrawable(R.drawable.rounded_transluscent_grey_rectangle));
        //mConnectionView.setTextColor(0xffffffff);
        mConnectionView.setConnectedSuccessDrawable(getResources().getDrawable(R.drawable.sphero_alive));
        mConnectionView.setConnectionFailedDrawable(getResources().getDrawable(R.drawable.sphero_dead));

        mConnectionView.setOnRobotConnectionEventListener(new SpheroConnectionView.OnRobotConnectionEventListener() {
            @Override
            public void onRobotConnected(Robot robot) {

                String id = robot.getUniqueId();

                mRobot = RobotProvider.getDefaultProvider().findRobot(id);
                mConnectionLayout.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onRobotConnectionFailed(Robot robot) {

            }

            @Override
            public void onNonePaired() {

                showGoSpheroDialog();
            }

            @Override
            public void onBluetoothNotEnabled() {

                //turn on bluetooth
                Intent i = RobotProvider.getDefaultProvider().getAdapterIntent();
                startActivity(i);
            }
        });
    }

    public void startSpeech(View view) {
        Intent request = new Intent(this, ATTSpeechActivity.class);
        request.putExtra(ATTSpeechActivity.EXTRA_RECOGNITION_URL, SPEECH_URL);
        request.putExtra(ATTSpeechActivity.EXTRA_SPEECH_CONTEXT, "Generic");
        request.putExtra(ATTSpeechActivity.EXTRA_BEARER_AUTH_TOKEN, mOAuthToken);
        startActivityForResult(request, REQUEST_SPEECH_INTERACTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SPEECH_INTERACTION) {
            if (resultCode == RESULT_OK) {
                List<String> textList = data.getStringArrayListExtra(ATTSpeechActivity.EXTRA_RESULT_TEXT_LIST);
                if (textList.isEmpty()) {
                    return;
                }

                // there's stuff
                StringBuilder builder = new StringBuilder("Phrase: ");
                for (String part : textList) {
                    builder.append(part);
                    builder.append(" ");
                }
                Log.d("Orbotix", builder.toString());
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mConnectionLayout.isShown() && mRobot == null) {
            mConnectionLayout.setVisibility(View.VISIBLE);
            mConnectionView.showSpheros();
        }

        SpeechWebServiceCall.setAppId("c6ec39b0e4c6d64068b2a7c42bce4a6e");
        SpeechWebServiceCall.setAppSecret("95750fcd760bd2be");

        SpeechWebServiceCall call = new SpeechWebServiceCall("https://api.att.com/oauth/token?client_id=c6ec39b0e4c6d64068b2a7c42bce4a6e&client_secret=95750fcd760bd2be&grant_type=client_credentials&scope=SPEECH");
        call.setListener(this);
        mOAuthToken = null;
        call.makeCallAsynchronously();
    }

    @Override
    protected void onStop() {
        super.onStop();
        RobotProvider.getDefaultProvider().disconnectControlledRobots();
        mRobot = null;

        mOAuthToken = null;
    }

    private void showGoSpheroDialog(){

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setCancelable(true)
                .setMessage(getString(R.string.no_sphero))
                .setNeutralButton(R.string.goto_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent settings_intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                        startActivity(settings_intent);
                        finish();
                    }
                })
                .setPositiveButton(R.string.BuySphero, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                        String url = "http://store.gosphero.com";
                        Intent buySpheroIntent = new Intent(Intent.ACTION_VIEW);
                        buySpheroIntent.setData(Uri.parse(url));
                        startActivity(buySpheroIntent);
                    }
                })
                .setCancelable(false);
        builder.show();
    }

    @Override
    public void onCallCompleted(SpeechWebServiceCall call, int speechResponseCode) {
        if (speechResponseCode == SpeechWebServiceCall.SPHEROVERSE_GOOD_CALL) {
            //Log.d("Orbotix", call.getData());

            try {
                JSONObject object = new JSONObject(call.getData());
                mOAuthToken = object.getString("access_token");
                Log.d("Orbotix", "token = " + mOAuthToken);
                mSpeakButton.setEnabled(true);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
        }
    }
}

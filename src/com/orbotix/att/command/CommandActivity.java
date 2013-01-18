package com.orbotix.att.command;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.att.android.speech.ATTSpeechActivity;
import orbotix.robot.base.*;
import orbotix.view.calibration.CalibrationButtonView;
import orbotix.view.connection.SpheroConnectionView;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Random;

public class CommandActivity extends Activity implements SpeechWebServiceCall.SpeechCallCompletedListener {

    private static final int REQUEST_SPEECH_INTERACTION = 1337;
    private static final String SPEECH_URL = "https://api.att.com/rest/2/SpeechToText";

    private SpheroConnectionView mConnectionView;
    private View mConnectionLayout;
    private Robot mRobot;
    private Button mSpeakButton;
    private TextView mSpokenTextView;
    private String mOAuthToken;
    private int mLastKnownHeading;

    private Handler mHandler = new Handler();

    private CalibrationButtonView mCalibrationButtonViewAbove;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mSpeakButton = (Button)findViewById(R.id.speak_button);
        mSpokenTextView = (TextView)findViewById(R.id.spoken_text_view);
        setupConnectionView();

        // Initialize calibrate button view where the calibration circle shows above button
        // This is the default behavior
        mCalibrationButtonViewAbove = (CalibrationButtonView)findViewById(R.id.calibration_above);
        mCalibrationButtonViewAbove.setCalibrationButton((View)findViewById(R.id.calibration_button_above));
        // You can also change the size of the calibration views
        mCalibrationButtonViewAbove.setRadius(300);
        mCalibrationButtonViewAbove.setCalibrationCircleLocation(CalibrationButtonView.CalibrationCircleLocation.ABOVE);
        mCalibrationButtonViewAbove.setOnEndRunnable(new Runnable() {
            @Override
            public void run() {
                mLastKnownHeading = 0;
            }
        });
    }

    private void setupConnectionView() {
        mConnectionLayout = findViewById(R.id.connection_layout);
        mConnectionView = (SpheroConnectionView)findViewById(R.id.connection_view);
        mConnectionView.setSingleSpheroMode(true);
        mConnectionView.setConnectedSuccessDrawable(getResources().getDrawable(R.drawable.sphero_alive));
        mConnectionView.setConnectionFailedDrawable(getResources().getDrawable(R.drawable.sphero_dead));

        mConnectionView.setOnRobotConnectionEventListener(new SpheroConnectionView.OnRobotConnectionEventListener() {
            @Override
            public void onRobotConnected(Robot robot) {

                String id = robot.getUniqueId();

                mRobot = RobotProvider.getDefaultProvider().findRobot(id);

                // Make sure you let the calibration views know the robot it should control
                mCalibrationButtonViewAbove.setRobot(mRobot);

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

                // stuff came back!
                StringBuilder builder = new StringBuilder();
                for (String part : textList) {
                    builder.append(part);
                    builder.append(" ");
                }

                mSpokenTextView.setText(builder.toString());

                processSpeech(builder.toString());
            }
        }
    }

    private void processSpeech(String phrase) {
        if (phrase.contains("go")) {
            handleGo(phrase);
        } else if (phrase.contains("change")) {
            handleChange(phrase);
        }

    }

    private void handleChange(String phrase) {
        String next = getNextWord("change", phrase);
        if (next.equalsIgnoreCase("color")) {
            Random random = new Random();
            RGBLEDOutputCommand.sendCommand(mRobot, random.nextInt(255), random.nextInt(255), random.nextInt(255));
        }
    }

    private void handleGo(String phrase) {
        String next = getNextWord("go", phrase);
        if (next.equalsIgnoreCase("forward")) {
            go(0.0);
        } else if (next.contains("back")) {
            go(180.0);
        } else if (next.equalsIgnoreCase("left")) {
            go(-90.0);
        } else if (next.equalsIgnoreCase("right")) {
            go(90.0);
        }
    }

    private String getNextWord(String foundWord, String phrase) {
        String next = phrase.substring(phrase.indexOf(foundWord) + foundWord.length() + 1);
        int nextSpaceIndex = next.indexOf(" ");
        if (nextSpaceIndex > 0) {
            next = next.substring(0, nextSpaceIndex);
        }

        //Log.d("Orbotix", foundWord + " " + next);

        return next;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mConnectionLayout.isShown() && mRobot == null) {
            mConnectionLayout.setVisibility(View.VISIBLE);
            mConnectionView.showSpheros();
        }

        SpeechWebServiceCall.setAppId("801f740768450fd78a5c7aaa16ac82b4");
        SpeechWebServiceCall.setAppSecret("31c6b5e9b54b5abb");

        SpeechWebServiceCall call = new SpeechWebServiceCall("https://api.att.com/oauth/token?client_id=801f740768450fd78a5c7aaa16ac82b4&client_secret=31c6b5e9b54b5abb&grant_type=client_credentials&scope=SPEECH");
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
    private Runnable stopper = new Runnable() {
        @Override
        public void run() {
            RollCommand.sendCommand(mRobot, convertAngleToDegrees(mLastKnownHeading), 0.0f, true);
        }
    };

    private void go(double headingChange) {
        if (mRobot == null || !mRobot.isUnderControl()) {
            return;
        }
        mLastKnownHeading += headingChange;
        RotationRateCommand.sendCommand(mRobot, 0.9f);
        RollCommand.sendCommand(mRobot, convertAngleToDegrees(mLastKnownHeading), 0.9f, false);

        mHandler.removeCallbacks(stopper);
        mHandler.postDelayed(stopper, 2000);
    }

    @Override
    public void onCallCompleted(SpeechWebServiceCall call, int speechResponseCode) {
        if (speechResponseCode == SpeechWebServiceCall.SPHEROVERSE_GOOD_CALL) {
            //Log.d("Orbotix", call.getData());

            try {
                JSONObject object = new JSONObject(call.getData());
                mOAuthToken = object.getString("access_token");
                //Log.d("Orbotix", "token = " + mOAuthToken);
                mSpeakButton.setEnabled(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        mCalibrationButtonViewAbove.interpretMotionEvent(event);
        return super.dispatchTouchEvent(event);
    }

    public static float convertAngleToDegrees(double angleInRadians) {
        float angleInDegrees = (float)Math.toDegrees(angleInRadians);
        if (angleInDegrees >= 0.0 && angleInDegrees < 360.0) {
            return angleInDegrees;
        } else if (angleInDegrees < 0.0) {
            return convertAngleToDegrees(angleInRadians + (2.0 * Math.PI));
        } else if (angleInDegrees > 360.0) {
            return convertAngleToDegrees(angleInRadians - (2.0 * Math.PI));
        } else {
            return Math.abs(angleInDegrees);
        }
    }
}

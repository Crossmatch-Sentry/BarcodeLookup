package com.crossmatch.barcodelookup;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.Set;

import static android.provider.ContactsContract.Intents.Insert.ACTION;

public class BarcodeActivity extends AppCompatActivity implements Runnable {

    private static final String LOG_TAG = "BarcodeActivity";
    //private static String lookupBaseURL = "http://athena.cmacu.net/cgi-bin/vmwmsgs.pl?command=Retrieve&name=wanted.jpg";

    // This is the name we are going to register with the Zebra DataWedge
    private static final String EXTRA_PROFILENAME = "BarcodeLookup";

    // Zebra DataWedge Extras
    private static final String EXTRA_GET_VERSION_INFO = "com.symbol.datawedge.api.GET_VERSION_INFO";
    private static final String EXTRA_CREATE_PROFILE = "com.symbol.datawedge.api.CREATE_PROFILE";
    private static final String EXTRA_KEY_APPLICATION_NAME = "com.symbol.datawedge.api.APPLICATION_NAME";
    private static final String EXTRA_KEY_NOTIFICATION_TYPE = "com.symbol.datawedge.api.NOTIFICATION_TYPE";
    private static final String EXTRA_SOFT_SCAN_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER";
    private static final String EXTRA_RESULT_NOTIFICATION = "com.symbol.datawedge.api.NOTIFICATION";
    private static final String EXTRA_REGISTER_NOTIFICATION = "com.symbol.datawedge.api.REGISTER_FOR_NOTIFICATION";
    private static final String EXTRA_UNREGISTER_NOTIFICATION = "com.symbol.datawedge.api.UNREGISTER_FOR_NOTIFICATION";
    private static final String EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG";

    private static final String EXTRA_RESULT_NOTIFICATION_TYPE = "NOTIFICATION_TYPE";
    private static final String EXTRA_KEY_VALUE_SCANNER_STATUS = "SCANNER_STATUS";
    private static final String EXTRA_KEY_VALUE_PROFILE_SWITCH = "PROFILE_SWITCH";
    private static final String EXTRA_KEY_VALUE_CONFIGURATION_UPDATE = "CONFIGURATION_UPDATE";
    private static final String EXTRA_KEY_VALUE_NOTIFICATION_STATUS = "STATUS";
    private static final String EXTRA_KEY_VALUE_NOTIFICATION_PROFILE_NAME = "PROFILE_NAME";
    private static final String EXTRA_SEND_RESULT = "SEND_RESULT";

    private static final String EXTRA_EMPTY = "";

    private static final String EXTRA_RESULT_GET_VERSION_INFO = "com.symbol.datawedge.api.RESULT_GET_VERSION_INFO";
    private static final String EXTRA_RESULT = "RESULT";
    private static final String EXTRA_RESULT_INFO = "RESULT_INFO";
    private static final String EXTRA_COMMAND = "COMMAND";

    // Zebra DataWedge Actions
    private static final String ACTION_DATAWEDGE = "com.symbol.datawedge.api.ACTION";
    private static final String ACTION_RESULT_NOTIFICATION = "com.symbol.datawedge.api.NOTIFICATION_ACTION";
    private static final String ACTION_RESULT = "com.symbol.datawedge.api.RESULT_ACTION";


    // private variables
    private Boolean bRequestSendResult = false;
    private int pageTimeout = 10;     // how long to wait (in seconds) for the image to load
    volatile boolean autoScanThread = false;
    Thread tAutoScan;

    private TextView barcodeText;
    private Button singleScanButton;
    private Button autoScanButton;
    public static Handler uiUpdateHandler;

    public static final int MESSAGE_BARCODE_STRING = 1;
    public static final int MESSAGE_COMMAND_COMPLETE = 2;
    public static final int MESSAGE_QUERY_STRING = 3;
    public static final int MESSAGE_START_SCAN    = 4;
    public static final int MESSAGE_STOP_SCAN     = 5;
    public static final int MESSAGE_QUERY_COMPORT = 10;
    public static final int MESSAGE_QUERY_READ_MODE = 11;
    public static final int MESSAGE_QUERY_DELAY_OF_READ = 12;
    public static final int MESSAGE_QUERY_SENSITIVITY = 13;
    public static final int MESSAGE_QUERY_SUFFIX_CHAR = 14;
    public static final int MESSAGE_QUERY_ERROR = 15;

    private PowerManager.WakeLock wl;
    private WifiManager wifiManager;
    private TextView linkspeedText;
    private WebView myWebView;
    private TextView rssiText;
    private TextView ssidText;
    private Timer timer;
    private ProgressDialog progressLoadUrl;
    private LookupWebViewClient webViewClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        setProgressBarVisibility(true);
        setContentView(R.layout.activity_barcode);
        //Toolbar toolbar = findViewById(R.id.toolbar);

        Log.i(LOG_TAG, "Barcode Lookup starting");

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        barcodeText = findViewById(R.id.lblScanData);
        //barcodeText.setText("Barcode Lookup");
        rssiText = findViewById(R.id.textViewrssi);
        ssidText = findViewById(R.id.textViewssid);
        linkspeedText = findViewById(R.id.textViewspeed);
        myWebView = findViewById(R.id.webview);
        singleScanButton = findViewById(R.id.scan_button);
        autoScanButton = findViewById(R.id.autoscan_btn);

        // Keep screen on while this app is running
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // setup wakelock for later
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "CMBARCODE:hidwl");


        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        uiUpdateHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                int cmd = msg.arg1;
                boolean msgHandled = true;  // set to true that we handled the message

                Log.i(LOG_TAG, "MainActivity::handleMessage recevied this msg " + msg.arg1 + " " + msg.obj);

                /* update our Wi-Fi status */
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                    ssidText.setText(wifiInfo.getSSID());
                    int j = wifiInfo.getLinkSpeed();
                    Log.i(LOG_TAG, "Link speed: "+j);
                    linkspeedText.setText(String.valueOf(j));
                    j = wifiInfo.getRssi();
                    Log.i(LOG_TAG, "RSSI: "+j);
                    rssiText.setText(String.valueOf(j));
                } else {
                    ssidText.setText(R.string.NOT_CONNECTED);
                    linkspeedText.setText("0");
                    rssiText.setText("0");
                }

                switch (cmd) {
                    case MESSAGE_BARCODE_STRING:
                        Log.i(LOG_TAG, "handleMessage writing text ");
                        String barcode = (String) msg.obj;
                        //barcodeText.setText(barcode);
                        DisplayImageURL(barcode);

                        wl.acquire(10000);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Log.i(LOG_TAG,"Thread sleep timed out");
                        }
                        wl.release();

                        break;
                    case MESSAGE_QUERY_STRING:
                        Log.i(LOG_TAG, "handleMessage writing query text ");
                        String query = (String) msg.obj;
                        barcodeText.setText(query);
                        break;
                    case MESSAGE_COMMAND_COMPLETE:
                        String result = (String) msg.obj;
                        Log.i(LOG_TAG,"Message command complete received: "+result);
                        //resultText.setText(result);
                        break;
                    case MESSAGE_QUERY_ERROR:
                        String queryErr = (String) msg.obj;
                        barcodeText.setText(queryErr);
                    default:
                        //super.handleMessage(msg);
                        msgHandled = false;
                }

                return msgHandled;
            }
        });

        // Register for status change notification
        // Use REGISTER_FOR_NOTIFICATION: http://techdocs.zebra.com/datawedge/latest/guide/api/registerfornotification/
        Bundle b = new Bundle();
        b.putString(EXTRA_KEY_APPLICATION_NAME, getPackageName());
        b.putString(EXTRA_KEY_NOTIFICATION_TYPE, "SCANNER_STATUS");     // register for changes in scanner status
        sendDataWedgeIntentWithExtra(ACTION_DATAWEDGE, EXTRA_REGISTER_NOTIFICATION, b);

        registerReceivers();

        // Get DataWedge version
        // Use GET_VERSION_INFO: http://techdocs.zebra.com/datawedge/latest/guide/api/getversioninfo/
        sendDataWedgeIntentWithExtra(ACTION_DATAWEDGE, EXTRA_GET_VERSION_INFO, EXTRA_EMPTY);    // must be called after registering BroadcastReceiver

        //bl = new BarcodeListener(this, uiUpdateHandler);
        //ConfigureZebraScanner();
        CreateZebraDWProfile();


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Toast.makeText(this,"Selected item: "+item.getTitle(), Toast.LENGTH_SHORT).show();
        switch (item.getItemId()) {
            case R.id.settings:
                Log.i(LOG_TAG, "Launch Settings menu");
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);

                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    public void startThread ()
    {
        tAutoScan = new Thread(this);
        autoScanThread = true;
        tAutoScan.start();
    }

    public void run() {
        Log.i(LOG_TAG, "Background timer thread starts: "+ autoScanThread);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String scantime = sharedPref.getString(getString(R.string.pref_scantime_key), "60");

        while(autoScanThread) {
            Log.i(LOG_TAG,"Start new scan every " +  scantime + " seconds");
            sendDataWedgeIntentWithExtra(ACTION_DATAWEDGE, EXTRA_SOFT_SCAN_TRIGGER, "TOGGLE_SCANNING");
            try {
                Thread.sleep(Integer.parseInt(scantime) * 1000L);
            } catch (InterruptedException e) {
                Log.i(LOG_TAG,"Thread sleep timed out");
            }
        }
        Log.i(LOG_TAG,"Background thread complete");

    }

    // Toggle soft scan trigger from UI onClick() event
    // Use SOFT_SCAN_TRIGGER: http://techdocs.zebra.com/datawedge/latest/guide/api/softscantrigger/
    public void AutoScanEnable (View view){
        Log.i(LOG_TAG,"Enabling auto scan...");
        if (autoScanThread) {
            Log.i(LOG_TAG,"Stopping auto scan...");
            autoScanButton.setText(R.string.autoscan_start_btn);
            singleScanButton.setEnabled(true);
            autoScanThread = false;
        } else {
            Log.i(LOG_TAG,"Starting auto scan...");
            autoScanButton.setText(R.string.autoscan_stop_btn);
            singleScanButton.setEnabled(false);
            startThread();
        }
    }

    private void CreateZebraDWProfile() {
        // Send DataWedge intent with extra to create profile
        // Use CREATE_PROFILE: http://techdocs.zebra.com/datawedge/latest/guide/api/createprofile/
        sendDataWedgeIntentWithExtra(ACTION_DATAWEDGE, EXTRA_CREATE_PROFILE, EXTRA_PROFILENAME);

        // Configure created profile to apply to this app
        Bundle profileConfig = new Bundle();
        profileConfig.putString("PROFILE_NAME", EXTRA_PROFILENAME);
        profileConfig.putString("PROFILE_ENABLED", "true");
        profileConfig.putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST");  // Create profile if it does not exist

        // Configure barcode input plugin
        Bundle barcodeConfig = new Bundle();
        barcodeConfig.putString("PLUGIN_NAME", "BARCODE");
        barcodeConfig.putString("RESET_CONFIG", "true"); //  This is the default
        Bundle barcodeProps = new Bundle();
        barcodeConfig.putBundle("PARAM_LIST", barcodeProps);
        profileConfig.putBundle("PLUGIN_CONFIG", barcodeConfig);

        // Associate profile with this app
        Bundle appConfig = new Bundle();
        appConfig.putString("PACKAGE_NAME", getPackageName());
        appConfig.putStringArray("ACTIVITY_LIST", new String[]{"*"});
        profileConfig.putParcelableArray("APP_LIST", new Bundle[]{appConfig});
        profileConfig.remove("PLUGIN_CONFIG");

        // Apply configs
        // Use SET_CONFIG: http://techdocs.zebra.com/datawedge/latest/guide/api/setconfig/
        sendDataWedgeIntentWithExtra(ACTION_DATAWEDGE, EXTRA_SET_CONFIG, profileConfig);

        // Configure intent output for captured data to be sent to this app
        Bundle intentConfig = new Bundle();
        intentConfig.putString("PLUGIN_NAME", "INTENT");
        intentConfig.putString("RESET_CONFIG", "true");
        Bundle intentProps = new Bundle();
        intentProps.putString("intent_output_enabled", "true");
        intentProps.putString("intent_action", "com.zebra.datacapture1.ACTION");
        intentProps.putString("intent_delivery", "2");
        intentConfig.putBundle("PARAM_LIST", intentProps);
        profileConfig.putBundle("PLUGIN_CONFIG", intentConfig);
        sendDataWedgeIntentWithExtra(ACTION_DATAWEDGE, EXTRA_SET_CONFIG, profileConfig);

        Toast.makeText(getApplicationContext(), "Created profile.  Check DataWedge app UI.", Toast.LENGTH_LONG).show();
    }

    /*
     * configure the Zebra scanner for our operational mode with code 128, code 39, upca, and ean13
     * codes enabled
     */
    private void ConfigureZebraScanner() {
        Log.i(LOG_TAG, "Configuring Zebra Scanner mode");
        // Main bundle properties
        Bundle profileConfig = new Bundle();
        profileConfig.putString("PROFILE_NAME", EXTRA_PROFILENAME);
        profileConfig.putString("PROFILE_ENABLED", "true");
        profileConfig.putString("CONFIG_MODE", "UPDATE");  // Update specified settings in profile

        // PLUGIN_CONFIG bundle properties
        Bundle barcodeConfig = new Bundle();
        barcodeConfig.putString("PLUGIN_NAME", "BARCODE");
        barcodeConfig.putString("RESET_CONFIG", "true");

        // PARAM_LIST bundle properties
        Bundle barcodeProps = new Bundle();
        barcodeProps.putString("scanner_selection", "auto");
        barcodeProps.putString("scanner_input_enabled", "true");
        barcodeProps.putString("decoder_code128", "true");
        barcodeProps.putString("decoder_code39", "true");
        barcodeProps.putString("decoder_ean13", "true");
        barcodeProps.putString("decoder_upca", "true");

        // Bundle "barcodeProps" within bundle "barcodeConfig"
        barcodeConfig.putBundle("PARAM_LIST", barcodeProps);
        // Place "barcodeConfig" bundle within main "profileConfig" bundle
        profileConfig.putBundle("PLUGIN_CONFIG", barcodeConfig);

        // Create APP_LIST bundle to associate app with profile
        Bundle appConfig = new Bundle();
        appConfig.putString("PACKAGE_NAME", getPackageName());
        appConfig.putStringArray("ACTIVITY_LIST", new String[]{"*"});
        profileConfig.putParcelableArray("APP_LIST", new Bundle[]{appConfig});
        sendDataWedgeIntentWithExtra(ACTION_DATAWEDGE, EXTRA_SET_CONFIG, profileConfig);
        Toast.makeText(getApplicationContext(), "In profile " + EXTRA_PROFILENAME, Toast.LENGTH_LONG).show();
    }

    // Toggle soft scan trigger from UI onClick() event
    // Use SOFT_SCAN_TRIGGER: http://techdocs.zebra.com/datawedge/latest/guide/api/softscantrigger/
    public void ToggleSoftScanTrigger (View view){
        sendDataWedgeIntentWithExtra(ACTION_DATAWEDGE, EXTRA_SOFT_SCAN_TRIGGER, "TOGGLE_SCANNING");
    }

    // Create filter for the broadcast intent
    private void registerReceivers() {

        Log.d(LOG_TAG, "registerReceivers()");

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_RESULT_NOTIFICATION);   // for notification result
        filter.addAction(ACTION_RESULT);                // for error code result
        filter.addCategory(Intent.CATEGORY_DEFAULT);    // needed to get version info

        // register to received broadcasts via DataWedge scanning
        filter.addAction(getResources().getString(R.string.activity_intent_filter_action));
        filter.addAction(getResources().getString(R.string.activity_action_from_service));
        registerReceiver(myBroadcastReceiver, filter);
    }

    // Unregister scanner status notification
    public void unRegisterScannerStatus() {
        Log.d(LOG_TAG, "unRegisterScannerStatus()");
        Bundle b = new Bundle();
        b.putString(EXTRA_KEY_APPLICATION_NAME, getPackageName());
        b.putString(EXTRA_KEY_NOTIFICATION_TYPE, EXTRA_KEY_VALUE_SCANNER_STATUS);
        Intent i = new Intent();
        i.setAction(ACTION);
        i.putExtra(EXTRA_UNREGISTER_NOTIFICATION, b);
        this.sendBroadcast(i);
    }


    private void DisplayImageURL(String barcode) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String host = sharedPref.getString(getString(R.string.pref_url_key), "athena.cmacu.net");
        StringBuilder url = new StringBuilder(host);
        url.append("/cgi-bin/vmwmsgs.pl?command=Retrieve&name=wanted.jpg&SerialNum=");
        url.append(barcode);
        Log.i(LOG_TAG,"URL: "+ url);
        webViewClient = new LookupWebViewClient(pageTimeout);
        myWebView.setWebViewClient(webViewClient);
        loadUrl(url.toString());
        //progressLoadUrl = ProgressDialog.show(this, getString(R.string.CONNECTING_TITLE), getString(R.string.CONNECTING_MSG));
        //myWebView.loadUrl(url.toString());

    }

    private void loadUrl(String url) {
        // show progres
        progressLoadUrl = ProgressDialog.show(this, getString(R.string.CONNECTING_TITLE), getString(R.string.CONNECTING_MSG));
        webViewClient.prepareToLoadUrl();
        myWebView.loadUrl(url);
    }

//    private void setSigninFailureResult() {
//        setResult(getResources().getInteger(R.integer.RESPONSE_FAILED_CODE));
//        finish();
//    }
//
//    private void setSigninResult() {
//        setResult(getResources().getInteger(R.integer.RESPONSE_OK_CODE));
//    }

    private class LookupWebViewClient extends WebViewClient {
        private int timeout;        // timeout for page loading
        private String urlLoading;
        boolean pageLoaded = false;
        boolean hasError = false;       // flag to instruct the client to ignore callbacks after error
        private Handler timeoutHandler;
        private AlertDialog alertDialog;

        private LookupWebViewClient(int timeout) {
            this.timeout = timeout;
            timeoutHandler = new Handler();
        }

        // Called by activity before requesting load of a URL
        private void prepareToLoadUrl() {
            this.hasError = false;
            this.pageLoaded = true;
            this.urlLoading = null;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.i(LOG_TAG,"onPageStarted: hasError = "+hasError);
            super.onPageStarted(view, url, favicon);
            if (hasError) {
                return;
            }
            urlLoading = url;
            // timeout has expired if this flag is still set when the message is handled
            pageLoaded = false;
            Runnable run = new Runnable() {
                @Override
                public void run() {
                    // Do nothing if we already have an error
                    if (hasError) {
                        return;
                    }

                    // Dismiss an current alerts and progress
                    dismissProgress();
                    dismissErrorAlert();
                    if (!pageLoaded) {
                        Log.e(LOG_TAG,"Page timed-out");
                        showTimeoutAlert();
                    }
                }
            };
            timeoutHandler.postDelayed(run, this.timeout* 1000L);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Log.e(LOG_TAG,"onReceivedError detected");
            // Ignore future callbacks because the page load has failed
            hasError = true;
            dismissProgress();
            showServerErrorAlert();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Log.i(LOG_TAG,"onPageFinished detected");
            if (hasError) {
                return;
            }
            pageLoaded = true;
            dismissProgress();
            dismissErrorAlert();
            urlLoading = null;

            // Do whatever processing you need to on page load here
        }

        private void showTimeoutAlert() {
            showErrorAlert(R.string.TIMEOUT_TITLE, R.string.TIMEOUT_MSG);
        }

        private void showServerErrorAlert() {
            showErrorAlert(R.string.SERVER_ERROR_TITLE,R.string.SERVER_ERROR_MESSAGE);
        }

        private void showErrorAlert(int titleResource, int messageResource) {
            AlertDialog.Builder builder = new AlertDialog.Builder(BarcodeActivity.this);
            // Add the buttons
            builder.setTitle(titleResource)
                    .setMessage(messageResource)
                    .setPositiveButton(R.string.RETRY, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // Try to load url again
                            Log.i(LOG_TAG,"Try to reload page");
                            loadUrl(urlLoading);
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(R.string.CANCEL, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                    //setSigninFailureResult();
                    loadUrl("about:blank");
                    dialog.cancel();
                }
            });

            // Create the AlertDialog
            alertDialog = builder.create();
            alertDialog.show();
        }

        private void dismissProgress() {
            if (progressLoadUrl != null && progressLoadUrl.isShowing()) {
                progressLoadUrl.dismiss();
            }
        }

        private void dismissErrorAlert() {
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
        }

    }


    private BroadcastReceiver myBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Bundle b = intent.getExtras();

            Log.d(LOG_TAG, "DataWedge Action:" + action);

            // Get DataWedge version info
            if (intent.hasExtra(EXTRA_RESULT_GET_VERSION_INFO))
            {
                Bundle versionInfo = intent.getBundleExtra(EXTRA_RESULT_GET_VERSION_INFO);
                String DWVersion = versionInfo.getString("DATAWEDGE");

                TextView txtDWVersion = findViewById(R.id.txtGetDWVersion);
                txtDWVersion.setText(DWVersion);
                Log.i(LOG_TAG, "DataWedge Version: " + DWVersion);
            }

            if (action.equals(getResources().getString(R.string.activity_intent_filter_action)))
            {
                //  Received a barcode scan
                try
                {
                    displayScanResult(intent, "via Broadcast");
                }
                catch (Exception e)
                {
                    //  Catch error if the UI does not exist when we receive the broadcast...
                }
            }

            else if (action.equals(ACTION_RESULT))
            {
                // Register to receive the result code
                if ((intent.hasExtra(EXTRA_RESULT)) && (intent.hasExtra(EXTRA_COMMAND)))
                {
                    String command = intent.getStringExtra(EXTRA_COMMAND);
                    String result = intent.getStringExtra(EXTRA_RESULT);
                    String info = "";

                    if (intent.hasExtra(EXTRA_RESULT_INFO))
                    {
                        Bundle result_info = intent.getBundleExtra(EXTRA_RESULT_INFO);
                        Set<String> keys = result_info.keySet();
                        for (String key : keys) {
                            Object object = result_info.get(key);
                            if (object instanceof String) {
                                info += key + ": " + object + "\n";
                            } else if (object instanceof String[]) {
                                String[] codes = (String[]) object;
                                for (String code : codes) {
                                    info += key + ": " + code + "\n";
                                }
                            }
                        }
                        Log.d(LOG_TAG, "Command: "+command+"\n" +
                                "Result: " +result+"\n" +
                                "Result Info: " + info + "\n");
                        Toast.makeText(getApplicationContext(), "Error Resulted. Command:" + command + "\nResult: " + result + "\nResult Info: " +info, Toast.LENGTH_LONG).show();
                    }
                }

            }

            // Register for scanner change notification
            else if (action.equals(ACTION_RESULT_NOTIFICATION))
            {
                if (intent.hasExtra(EXTRA_RESULT_NOTIFICATION))
                {
                    Bundle extras = intent.getBundleExtra(EXTRA_RESULT_NOTIFICATION);
                    String notificationType = extras.getString(EXTRA_RESULT_NOTIFICATION_TYPE);
                    if (notificationType != null)
                    {
                        switch (notificationType) {
                            case EXTRA_KEY_VALUE_SCANNER_STATUS:
                                // Change in scanner status occurred
                                String displayScannerStatusText = extras.getString(EXTRA_KEY_VALUE_NOTIFICATION_STATUS) +
                                        ", profile: " + extras.getString(EXTRA_KEY_VALUE_NOTIFICATION_PROFILE_NAME);
                                //Toast.makeText(getApplicationContext(), displayScannerStatusText, Toast.LENGTH_SHORT).show();
                                final TextView lblScannerStatus = findViewById(R.id.lblScannerStatus);
                                lblScannerStatus.setText(displayScannerStatusText);
                                Log.i(LOG_TAG, "Scanner status: " + displayScannerStatusText);
                                break;

                            case EXTRA_KEY_VALUE_PROFILE_SWITCH:
                                // Received change in profile
                                // For future enhancement
                                break;

                            case  EXTRA_KEY_VALUE_CONFIGURATION_UPDATE:
                                // Configuration change occurred
                                // For future enhancement
                                break;
                        }
                    }
                }
            }
        }
    };

    private void displayScanResult(Intent initiatingIntent, String howDataReceived)
    {
        // store decoded data
        String decodedData = initiatingIntent.getStringExtra(getResources().getString(R.string.datawedge_intent_key_data));
        // store decoder type
        String decodedLabelType = initiatingIntent.getStringExtra(getResources().getString(R.string.datawedge_intent_key_label_type));

        final TextView lblScanData = findViewById(R.id.lblScanData);
        final TextView lblScanLabelType = findViewById(R.id.lblScanDecoder);

        lblScanData.setText(decodedData);
        lblScanLabelType.setText(decodedLabelType);

        // Re-use existing uihandler
        Message msg = uiUpdateHandler.obtainMessage();
        msg.arg1 = BarcodeActivity.MESSAGE_BARCODE_STRING;
        msg.obj = decodedData;
        uiUpdateHandler.sendMessage(msg);
    }

    private void sendDataWedgeIntentWithExtra(String action, String extraKey, Bundle extras)
    {
        Intent dwIntent = new Intent();
        dwIntent.setAction(action);
        dwIntent.putExtra(extraKey, extras);
        if (bRequestSendResult)
            dwIntent.putExtra(EXTRA_SEND_RESULT, "true");
        this.sendBroadcast(dwIntent);
    }

    private void sendDataWedgeIntentWithExtra(String action, String extraKey, String extraValue)
    {
        Intent dwIntent = new Intent();
        dwIntent.setAction(action);
        dwIntent.putExtra(extraKey, extraValue);
        if (bRequestSendResult)
            dwIntent.putExtra(EXTRA_SEND_RESULT, "true");
        this.sendBroadcast(dwIntent);
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onDestroy()
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.i(LOG_TAG, "onDestroy");
        uiUpdateHandler = null;
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onStart()
     */
    @Override
    protected void onStart() {
        super.onStart();
        Log.i(LOG_TAG, "onStart");
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onStart()
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(LOG_TAG, "onResume");
        registerReceivers();
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onStart()
     */
    @Override
    protected void onPause() {
        Log.i(LOG_TAG, "onPause");
        super.onPause();
        unregisterReceiver(myBroadcastReceiver);
        unRegisterScannerStatus();
    }

    @Override
    protected void onStop()
    {
        super.onStop();
    }

}

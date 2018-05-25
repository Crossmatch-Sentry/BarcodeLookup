package com.crossmatch.barcodelookup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.*;
import android.widget.TextView;
import android.widget.Toast;

import com.crossmatch.libbarcode.LibBarcode;
import com.crossmatch.libbarcode.LibBarcode.Devices;

import java.util.Timer;

public class BarcodeActivity extends AppCompatActivity {

    private static final String LOG_TAG = "BarcodeActivity";
    //private static String lookupBaseURL = "http://athena.cmacu.net/cgi-bin/vmwmsgs.pl?command=Retrieve&name=wanted.jpg";

    private int pageTimeout = 10;     // how long to wait (in seconds) for the image to load

    private TextView barcodeText;
    public static LibBarcode lb;
    public static BarcodeListener bl;
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

        barcodeText = (TextView) findViewById(R.id.console);
        //barcodeText.setText("Barcode Lookup");
        rssiText = (TextView) findViewById(R.id.textViewrssi);
        ssidText = (TextView)findViewById(R.id.textViewssid);
        linkspeedText = (TextView)findViewById(R.id.textViewspeed);
        myWebView = (WebView)findViewById(R.id.webview);

        if (lb != null) lb.close();
        lb = new LibBarcode(this, Devices.BARCODE_JE227);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "CMBARCODE");

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
                        barcodeText.setText(barcode);
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

        bl = new BarcodeListener(this, uiUpdateHandler);

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

    private void DisplayImageURL(String barcode) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String host = sharedPref.getString(getString(R.string.pref_url_key), "athena.cmacu.net");
        StringBuilder url = new StringBuilder(host);
        url.append("/cgi-bin/vmwmsgs.pl?command=Retrieve&name=wanted.jpg&SerialNum=");
        url.append(barcode);
        Log.i(LOG_TAG,"URL: "+url.toString());
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
            timeoutHandler.postDelayed(run, this.timeout*1000);
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

    /* (non-Javadoc)
     * @see android.app.Activity#onDestroy()
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.i(LOG_TAG, "onDestroy");
        uiUpdateHandler = null;
        lb.close();

        // Kill process to clean up Zebra usb interface.

        int pid = android.os.Process.myPid();
        android.os.Process.killProcess(pid);

    }

    /* (non-Javadoc)
     * @see android.app.Activity#onStart()
     */
    @Override
    protected void onStart() {
        super.onStart();
        Log.i(LOG_TAG, "onStart");
        if (lb != null) {
            lb.probe();
            //setProgrammingState(false);
        }
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onStart()
     */
    @Override
    protected void onResume() {
        super.onResume();

        Log.i(LOG_TAG, "onResume");
        if (lb != null) {
            bl = new BarcodeListener(this, uiUpdateHandler);
            lb.resume();
        }

    }

    /* (non-Javadoc)
     * @see android.app.Activity#onStart()
     */
    @Override
    protected void onPause() {
        Log.i(LOG_TAG, "onPause");
        if (lb != null) {
            lb.pause();
        }
        super.onPause();
    }


}

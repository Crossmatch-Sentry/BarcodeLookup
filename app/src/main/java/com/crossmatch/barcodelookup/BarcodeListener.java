package com.crossmatch.barcodelookup;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.crossmatch.libbarcode.LibBarcode;

public class BarcodeListener {
    private static final String LOG_TAG = "BarcodeListener";

    private LibBarcode lb = null;
    private Context context = null;
    public static Handler handler;

    BarcodeListener(Context c, Handler h ) {
        lb = BarcodeActivity.lb;
        context = c;
        handler = h;
        setupBarcodeListener();
    }

    private void setupBarcodeListener() {
        lb.setLibBarcodeListener(new LibBarcode.LibBarcodeListener() {
            @Override
            public void onBarcodeDataAvailable(String arg0) {
                Log.i(LOG_TAG, "BarcodeListener:onBarcodeDataAvailable recevied this string [" + arg0	+ "]");
                Message msg = handler.obtainMessage();
                msg.arg1 = BarcodeActivity.MESSAGE_BARCODE_STRING;
                msg.obj = arg0;
                handler.sendMessage(msg);
            }

            @Override
            public void onBarcodeByteDataAvailable(byte[] arg0) {
                Log.i(LOG_TAG,
                        "BarcodeListener:onBarcodeByteDataAvailable recevied this string ["
                                + arg0 + "]");
                String strArg0 = new String(arg0);
                Message msg = handler.obtainMessage();
                msg.arg1 = BarcodeActivity.MESSAGE_BARCODE_STRING;
                msg.obj = strArg0;
                handler.sendMessage(msg);
            }

            @Override
            public void onCommandResultAvailable(String command, int value) {
                Log.i(LOG_TAG,"BarcodeListener::onCommandResultAvailable " + command + " " + value);
                if (value == LibBarcode.RESULT_SUCCESS ) {
                    sendUiUpdateMessage(BarcodeActivity.MESSAGE_COMMAND_COMPLETE, command + " Successful", 0);
                } else {
                    sendUiUpdateMessage(BarcodeActivity.MESSAGE_COMMAND_COMPLETE, command + " Failed", 0);
                }
            }

            @Override
            public void onQueryResultAvailable(String command, String value) {
                Log.i(LOG_TAG,"BarcodeListener::onQueryResultAvailable " + command + " " + value);
                sendUiUpdateMessage(BarcodeActivity.MESSAGE_QUERY_STRING, command + " " + value, 0);
            }


        });
    }

    public void sendUiUpdateMessage(int message, String obj, int delay){
        Message msg = handler.obtainMessage();
        msg.arg1 = message;
        msg.obj = obj;
        if (delay > 0) {
            handler.sendMessageDelayed(msg, delay);

        } else {
            handler.sendMessage(msg);
        }
    }

}

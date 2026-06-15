package de.perfact.cordova.honeywell;

import android.content.Intent;
import android.os.Build;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

public class HoneywellScanner extends CordovaPlugin {

    private static final String HONEYWELL_PACKAGE = "com.honeywell.aidc";
    private static final String ACTION_CLAIM   = "com.honeywell.aidc.action.ACTION_CLAIM_SCANNER";
    private static final String ACTION_RELEASE = "com.honeywell.aidc.action.ACTION_RELEASE_SCANNER";

    private boolean isHoneywell;

    // Tracks the JS-requested state so lifecycle events can restore it.
    // true = enabled (scanner released, Intent Output Mode active)
    // false = disabled (scanner claimed, trigger inert)
    private boolean intendedEnabled = true;

    @Override
    protected void pluginInitialize() {
        isHoneywell = Build.MANUFACTURER.equalsIgnoreCase("honeywell");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        if ("setScannerEnabled".equals(action)) {
            boolean enabled = args.getBoolean(0);
            setScannerEnabled(enabled, callbackContext);
            return true;
        }
        return false;
    }

    private void setScannerEnabled(boolean enabled, CallbackContext callbackContext) {
        if (!isHoneywell) {
            callbackContext.error("Not a Honeywell device");
            return;
        }
        intendedEnabled = enabled;
        sendScannerBroadcast(enabled);
        callbackContext.success();
    }

    // enabled=true  → ACTION_RELEASE (return scanner to Intent Output Mode)
    // enabled=false → ACTION_CLAIM   (take exclusive control, trigger becomes inert)
    private void sendScannerBroadcast(boolean enabled) {
        String intentAction = enabled ? ACTION_RELEASE : ACTION_CLAIM;
        Intent intent = new Intent(intentAction);
        intent.setPackage(HONEYWELL_PACKAGE);
        cordova.getActivity().getApplicationContext().sendBroadcast(intent);
    }

    @Override
    public void onPause(boolean multitasking) {
        if (isHoneywell && !intendedEnabled) {
            // Release the scanner so the foregrounded app can use it.
            sendScannerBroadcast(true);
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        if (isHoneywell && !intendedEnabled) {
            // Re-claim: restore the disabled state requested by the JS layer.
            sendScannerBroadcast(false);
        }
    }
}

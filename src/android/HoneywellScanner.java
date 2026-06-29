package de.perfact.cordova.honeywell;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

public class HoneywellScanner extends CordovaPlugin {

    // The DataCollection service that receives these broadcasts is the Intermec-heritage
    // service, NOT "com.honeywell.aidc" (which is only the SDK's Java namespace). Targeting
    // the wrong package makes sendBroadcast() a silent no-op.
    private static final String DATACOLLECTION_PACKAGE = "com.intermec.datacollectionservice";
    private static final String ACTION_CLAIM   = "com.honeywell.aidc.action.ACTION_CLAIM_SCANNER";
    private static final String ACTION_RELEASE = "com.honeywell.aidc.action.ACTION_RELEASE_SCANNER";

    private static final String EXTRA_SCANNER    = "com.honeywell.aidc.extra.EXTRA_SCANNER";
    private static final String EXTRA_PROPERTIES = "com.honeywell.aidc.extra.EXTRA_PROPERTIES";

    private static final String SCANNER_IMAGER = "dcs.scanner.imager";

    // Trigger-control property override carried in EXTRA_PROPERTIES. It is non-persistent and
    // only applies for the duration of the claim, so it must be re-sent on every claim.
    private static final String PROP_TRIGGER_CONTROL_MODE = "TRIG_CONTROL_MODE";
    private static final String TRIGGER_CONTROL_DISABLE   = "disable";

    private boolean isHoneywell;

    // Tracks the JS-requested state so lifecycle events can restore it.
    // true  = enabled  (scanner released, hardware trigger live / Intent Output Mode active)
    // false = disabled (scanner claimed with trigger control mode "disable", trigger inert)
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
        applyScannerState(enabled);
        // sendBroadcast() is fire-and-forget: the DataCollection service reports no synchronous
        // result, so success here means "intent dispatched", not "trigger confirmed disabled".
        callbackContext.success();
    }

    // enabled=true  → ACTION_RELEASE: return the scanner to its default profile (trigger live).
    // enabled=false → ACTION_CLAIM carrying EXTRA_PROPERTIES with the trigger control mode set to
    //                 "disable", which makes the hardware scan trigger inert. Claiming without
    //                 these properties leaves the trigger live, so the bundle is essential.
    private void applyScannerState(boolean enabled) {
        Intent intent;
        if (enabled) {
            intent = new Intent(ACTION_RELEASE);
        } else {
            Bundle properties = new Bundle();
            properties.putString(PROP_TRIGGER_CONTROL_MODE, TRIGGER_CONTROL_DISABLE);

            intent = new Intent(ACTION_CLAIM);
            intent.putExtra(EXTRA_SCANNER, SCANNER_IMAGER);
            intent.putExtra(EXTRA_PROPERTIES, properties);
        }
        intent.setPackage(DATACOLLECTION_PACKAGE);
        cordova.getActivity().getApplicationContext().sendBroadcast(intent);
    }

    @Override
    public void onPause(boolean multitasking) {
        if (isHoneywell && !intendedEnabled) {
            // Release the scanner so the foregrounded app can use it.
            applyScannerState(true);
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        if (isHoneywell && !intendedEnabled) {
            // Re-claim and re-apply the disabled trigger state requested by the JS layer.
            applyScannerState(false);
        }
    }
}

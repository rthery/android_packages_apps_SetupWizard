/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import static com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.service.persistentdata.PersistentDataBlockManager;
import android.util.Log;

public class FrpActivity extends SubBaseActivity {

    @Override
    protected void onStartSubactivity() {
        Intent intent = getConfirmFrpCredentialIntent(this);
        if (intent == null) {
            finishAction(RESULT_SKIP);
            return;
        }
        startSubactivity(intent);
    }

    public static boolean isFrpActive(Context context) {
        try {
            PersistentDataBlockManager pdb =
                    context.getSystemService(PersistentDataBlockManager.class);
            return pdb != null && pdb.isFactoryResetProtectionActive();
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot check factory reset protection", e);
            return false;
        }
    }

    /** Returns null when FRP is off or cannot be cleared with the previous screen lock. */
    public static Intent getConfirmFrpCredentialIntent(Context context) {
        if (!isFrpActive(context)) {
            return null;
        }
        try {
            return context.getSystemService(KeyguardManager.class)
                    .createConfirmFactoryResetCredentialIntent(
                            context.getString(R.string.frp_title),
                            context.getString(R.string.frp_summary),
                            context.getString(R.string.skip));
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot confirm the factory reset protection credential", e);
            return null;
        }
    }
}

package org.telegram.circles;

import android.content.Context;

import org.telegram.circles.utils.Logger;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

public abstract class SuccessListener {
    private final Context context;
    private final BaseFragment baseFragment;

    public SuccessListener(Context context, BaseFragment baseFragment) {
        this.context = context;
        this.baseFragment = baseFragment;
    }

    public abstract void onSuccess();

    public void onError(Throwable error) {
        String message;
        if (error instanceof RequestError) {
            RequestError.ErrorCode code = ((RequestError) error).code;
            if (code == RequestError.ErrorCode.TLRPC) {
                message = ((RequestError) error).error.text;
                Logger.e(((RequestError) error).error);
            } else if (code != null) {
                message = context.getString(code.message);
                Logger.e(message);
            } else {
                message = context.getString(R.string.circles_unknown_error);
                Logger.e(message);
            }
        } else {
            Logger.e(error);
            message = error.getMessage();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setMessage(message);
        baseFragment.showDialog(builder.create());
    }
}

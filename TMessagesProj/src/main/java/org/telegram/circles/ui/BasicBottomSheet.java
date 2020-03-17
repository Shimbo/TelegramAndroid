package org.telegram.circles.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

abstract class BasicBottomSheet extends BottomSheet {
    public BasicBottomSheet(final Context context) {
        super(context, true);

        isFullscreen = false;

        containerView = new SizeNotifierFrameLayout(context, false);

        View content = createView((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE), containerView);

        containerView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 120, 0, 0));
        containerView.setOnClickListener(v -> dismiss());
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    protected abstract View createView(LayoutInflater inflater, ViewGroup parent);
}

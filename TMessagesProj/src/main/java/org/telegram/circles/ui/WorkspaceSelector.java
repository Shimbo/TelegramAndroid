package org.telegram.circles.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.circles.CircleType;
import org.telegram.circles.Circles;
import org.telegram.circles.SuccessListener;
import org.telegram.circles.data.CircleData;
import org.telegram.circles.utils.Logger;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class WorkspaceSelector extends BasicBottomSheet {
    private final int currentAccount;
    private final BaseFragment baseFragment;
    private final Collection<Long> dialogsToMove;
    private RecyclerView recyclerView;
    private ProgressBar progress;
    private TextView errorMessageView;
    private WorkspacesAdapter adapter;

    public WorkspaceSelector(int currentAccount, BaseFragment baseFragment) {
        this(currentAccount, baseFragment, null);
    }

    public WorkspaceSelector(int currentAccount, BaseFragment baseFragment, Collection<Long> dialogsToMove) {
        super(baseFragment.getParentActivity());
        this.baseFragment = baseFragment;
        this.currentAccount = currentAccount;
        this.dialogsToMove = dialogsToMove;
        loadData(false);
    }

    private void loadData(boolean doReload) {
        List<CircleData> circles = Circles.getInstance(currentAccount).getCachedCircles();
        if (!circles.isEmpty()) {
            hideProgress();
            adapter.setData(circles);
        }
        if (doReload) {
            Circles.getInstance(currentAccount).loadCircles(new SuccessListener(getContext(), null) {
                @Override
                public void onSuccess() {
                    hideProgress();
                    List<CircleData> data = Circles.getInstance(currentAccount).getCachedCircles();
                    adapter.setData(data);
                }

                @Override
                public String onError(Throwable error) {
                    String errorMessage = super.onError(error);
                    showError(errorMessage);
                    return errorMessage;
                }
            });
        }
    }

    void showProgress() {
        progress.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        errorMessageView.setVisibility(View.GONE);
    }

    void hideProgress() {
        progress.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        errorMessageView.setVisibility(View.GONE);
    }

    void showError(String errorMessage) {
        progress.setVisibility(View.GONE);
        errorMessageView.setVisibility(View.VISIBLE);
        errorMessageView.setText(errorMessage);
        recyclerView.setVisibility(View.GONE);
    }

    @Override
    protected View createView(LayoutInflater inflater, ViewGroup parent) {
        View rootView = inflater.inflate(R.layout.worspace_selector, parent, false);
        rootView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        progress = rootView.findViewById(R.id.progress);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progress.setIndeterminateTintList(ColorStateList.valueOf(Theme.getColor(Theme.key_avatar_backgroundSaved)));
        }
        recyclerView = rootView.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new WorkspacesAdapter(getContext());
        recyclerView.setAdapter(adapter);
        errorMessageView = rootView.findViewById(R.id.error_message_view);
        errorMessageView.setTextColor(Theme.getColor(Theme.key_chats_sentError));
        return rootView;
    }

    private void showCreateNewWorkspaceDialog() {
        Context context = baseFragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.new_circle));

        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintText(context.getString(R.string.circle_name));
        editText.setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setTransformHintToHeader(true);
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.requestFocus();
        editText.setPadding(0, 0, 0, 0);
        builder.setView(editText);

        builder.setNegativeButton(context.getString(R.string.circle_cancel), null);
        builder.setPositiveButton(context.getString(R.string.circle_create), (dialog, which) -> {
            showProgress();
            Circles.getInstance(currentAccount).createWorkspace(editText.getText().toString(), new SuccessListener(context, null) {
                @Override
                public void onSuccess() {
                    loadData(true);
                }

                @Override
                public String onError(Throwable error) {
                    hideProgress();
                    return super.onError(error);
                }
            });
        });
        builder.create().show();

        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
        if (layoutParams != null) {
            if (layoutParams instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
            }
            layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.height = AndroidUtilities.dp(36);
            editText.setLayoutParams(layoutParams);
        }
    }

    private class WorkspacesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private LayoutInflater inflater;
        private List<CircleData> circles = new ArrayList<>();

        WorkspacesAdapter(Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        void setData(List<CircleData> circles) {
            if (dialogsToMove != null) {
                for (Iterator<CircleData> i = circles.iterator(); i.hasNext();) {
                    if (i.next().circleType == CircleType.ARCHIVE) {
                        i.remove();
                    }
                }
            }
            this.circles = circles;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return circles.size() + (dialogsToMove == null ? 1 : 0);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.workspace_selector_item, parent, false);
            return new WorkspaceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (dialogsToMove != null || position < getItemCount()-1) {
                ((WorkspaceViewHolder) holder).bind(circles.get(position));
            } else {
                ((WorkspaceViewHolder) holder).bind(null);
            }
        }
    }

    private class WorkspaceViewHolder extends RecyclerListView.Holder {
        private TextView litera, name, counter, proIndicator;
        private ImageView icon, lock;
        private GradientDrawable iconBg;
        private static final float lockedAlpha = 0.6f;

        @SuppressLint("NewApi")
        WorkspaceViewHolder(View view) {
            super(view);

            StateListDrawable itemBg = (StateListDrawable) view.getResources().getDrawable(R.drawable.circles_top_bottom_border_bg);
            try {
                for (int i = 0; i < itemBg.getStateCount(); i++) {
                    Drawable item = itemBg.getStateDrawable(i);
                    if (item instanceof GradientDrawable) {
                        ((GradientDrawable) item).setColor(Theme.getColor(Theme.key_windowBackgroundChecked));
                    } else if (item instanceof LayerDrawable) {
                        ((GradientDrawable) ((LayerDrawable) item).getDrawable(0)).setColor(Theme.getColor(Theme.key_dialogBackgroundGray));
                        ((GradientDrawable) ((LayerDrawable) item).getDrawable(1)).setColor(Theme.getColor(Theme.key_dialogBackground));
                    }
                }
            } catch (Exception e) {
                Logger.e(e);
            }
            view.setBackground(itemBg);

            icon = view.findViewById(R.id.icon);
            iconBg = (GradientDrawable) icon.getResources().getDrawable(R.drawable.circles_workspace_gray_bg).mutate();
            iconBg.setColor(Theme.getColor(Theme.key_avatar_backgroundArchived));

            lock = view.findViewById(R.id.lock);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                lock.setImageTintList(ColorStateList.valueOf(Theme.getColor(Theme.key_chats_name)));
            }

            litera = view.findViewById(R.id.litera);
            GradientDrawable literaBg = (GradientDrawable) litera.getResources().getDrawable(R.drawable.circles_workspace_blue_bg).mutate();
            literaBg.setColor(Theme.getColor(Theme.key_avatar_backgroundSaved));
            litera.setBackground(literaBg);

            name = view.findViewById(R.id.name);

            counter = view.findViewById(R.id.counter);
            GradientDrawable counterBg = (GradientDrawable) counter.getResources().getDrawable(R.drawable.circles_counter_bg).mutate();
            counterBg.setColor(Theme.getColor(Theme.key_chats_unreadCounter));
            counter.setBackground(counterBg);
            counter.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText));

            proIndicator = view.findViewById(R.id.pro_indicator);
        }

        void bind(final CircleData circle) {
            if (circle == null) {
                icon.setVisibility(View.VISIBLE);
                icon.setImageResource(R.drawable.circles_create);
                icon.setPadding(0, 0, 0, 0 );
                icon.setBackground(null);
                litera.setVisibility(View.GONE);
                name.setText(R.string.circles_create);
                name.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn));

                lock.setVisibility(View.GONE);
                counter.setVisibility(View.GONE);
                proIndicator.setVisibility(View.GONE);
                name.setAlpha(1f);
                itemView.setOnClickListener( v -> {
                    showCreateNewWorkspaceDialog();
                });
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    icon.setImageTintList(ColorStateList.valueOf(Theme.getColor(Theme.key_chat_messageLinkIn)));
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    icon.setImageTintList(ColorStateList.valueOf(Theme.getColor(Theme.key_avatar_text)));
                }
                String circleName = "";
                switch (circle.circleType) {
                    case PERSONAL:
                    case ARCHIVE:
                        icon.setVisibility(View.VISIBLE);
                        litera.setVisibility(View.GONE);
                        int padding = AndroidUtilities.dp(9);
                        icon.setPadding(padding, padding, padding, padding);
                        icon.setBackground(iconBg);
                        icon.setImageResource(circle.circleType == CircleType.PERSONAL ? R.drawable.circles_personal : R.drawable.circles_archive);
                        circleName = itemView.getContext().getString(circle.circleType == CircleType.PERSONAL ? R.string.circles_personal : R.string.circles_archive);
                        break;
                    case WORKSPACE:
                        icon.setVisibility(View.GONE);
                        litera.setVisibility(View.VISIBLE);
                        String l = circle.name.substring(0, 1);
                        String[] parts = circle.name.split(" ");
                        if (parts.length > 1) {
                            l += parts[1].substring(0, 1);
                        }
                        litera.setText(l.toUpperCase());
                        circleName = circle.name;
                        break;
                }
                lock.setVisibility(circle.isLocked() ? View.VISIBLE : View.GONE);
                proIndicator.setVisibility(!circle.isLocked() && circle.isPaid() ? View.VISIBLE : View.GONE);
                if (circle.isLocked()) {
                    name.setAlpha(lockedAlpha);
                    lock.setAlpha(lockedAlpha);
                    litera.setAlpha(lockedAlpha);
                } else {
                    name.setAlpha(1f);
                    lock.setAlpha(1f);
                    litera.setAlpha(1f);
                }
                name.setText(circleName);
                name.setTextColor(Theme.getColor(Theme.key_chats_name));
                counter.setText(String.valueOf(circle.counter));
                counter.setVisibility(circle.counter > 0 ? View.VISIBLE : View.GONE);
                itemView.setOnClickListener( v -> {
                    if (dialogsToMove != null && circle.isLocked()) {
                        return;
                    }
                    long currentCircleId = Circles.getInstance(currentAccount).getSelectedCircle();
                    Circles.getInstance(currentAccount).setSelectedCircle(circle);
                    if (circle.circleType == CircleType.ARCHIVE) {
                        Bundle args = new Bundle();
                        args.putInt("folderId", 1);
                        baseFragment.presentFragment(new DialogsActivity(args));
                    }
                    if (dialogsToMove != null) {
                        showProgress();
                        Circles.getInstance(currentAccount).moveDialogs(currentCircleId, circle.id, dialogsToMove, new SuccessListener(baseFragment.getParentActivity(), baseFragment){
                            @Override
                            public void onSuccess() {
                                dismiss();
                            }

                            @Override
                            public String onError(Throwable error) {
                                hideProgress();
                                return super.onError(error);
                            }
                        });
                    } else {
                        dismiss();
                    }
                });
            }
        }
    }
}

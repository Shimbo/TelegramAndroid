package org.telegram.circles.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.upstream.cache.CacheDataSource;

import org.telegram.circles.CircleType;
import org.telegram.circles.Circles;
import org.telegram.circles.SuccessListener;
import org.telegram.circles.data.CircleData;
import org.telegram.circles.utils.Logger;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.List;

public class WorkspaceSelector extends BasicBottomSheet {
    private BaseFragment baseFragment;
    private RecyclerView recyclerView;
    private View progress;
    private TextView errorMessageView;
    private WorkspacesAdapter adapter;

    public WorkspaceSelector(BaseFragment baseFragment) {
        super(baseFragment.getParentActivity());
        this.baseFragment = baseFragment;
        loadData();
    }

    private void loadData() {
        List<CircleData> circles = Circles.getInstance().getCachedCircles();
        if (!circles.isEmpty()) {
            hideProgress();
            adapter.setData(circles);
        }
        Circles.getInstance().loadCircles(new SuccessListener(getContext(), null) {
            @Override
            public void onSuccess() {
                hideProgress();
                List<CircleData> data = Circles.getInstance().getCachedCircles();
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
        progress = rootView.findViewById(R.id.progress);
        recyclerView = rootView.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new WorkspacesAdapter(getContext());
        recyclerView.setAdapter(adapter);
        errorMessageView = rootView.findViewById(R.id.error_message_view);
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
            Circles.getInstance().createWorkspace(editText.getText().toString(), new SuccessListener(context, null) {
                @Override
                public void onSuccess() {
                    loadData();
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
            this.circles = circles;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return circles.size() + 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.workspace_selector_item, parent, false);
            return new WorkspaceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position < getItemCount()-1) {
                ((WorkspaceViewHolder) holder).bind(circles.get(position));
            } else {
                ((WorkspaceViewHolder) holder).bind(null);
            }
        }
    }

    private class WorkspaceViewHolder extends RecyclerListView.Holder {
        private TextView litera, name, counter;
        private ImageView icon;

        WorkspaceViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon);
            litera = view.findViewById(R.id.litera);
            name = view.findViewById(R.id.name);
            counter = view.findViewById(R.id.counter);
        }

        void bind(final CircleData circle) {
            if (circle == null) {
                icon.setVisibility(View.VISIBLE);
                icon.setImageResource(R.drawable.circles_create);
                icon.setPadding(0, 0, 0, 0 );
                icon.setBackground(null);
                litera.setVisibility(View.GONE);
                name.setText(R.string.circles_create);
                name.setTextColor(name.getResources().getColor(R.color.circles_create));
                counter.setVisibility(View.GONE);
                itemView.setOnClickListener( v -> {
                    showCreateNewWorkspaceDialog();
                });
            } else {
                switch (circle.circleType) {
                    case PERSONAL:
                    case ARCHIVE:
                        icon.setVisibility(View.VISIBLE);
                        litera.setVisibility(View.GONE);
                        int padding = AndroidUtilities.dp(9);
                        icon.setPadding(padding, padding, padding, padding);
                        icon.setBackgroundResource(R.drawable.circles_workspace_gray_bg);
                        icon.setImageResource(circle.circleType == CircleType.PERSONAL ? R.drawable.circles_personal : R.drawable.circles_archive);
                        name.setText(circle.circleType == CircleType.PERSONAL ? R.string.circles_personal : R.string.circles_archive);
                        break;
                    case WORKSPACE:
                        icon.setVisibility(View.GONE);
                        litera.setVisibility(View.VISIBLE);
                        litera.setText(circle.name.substring(0, 1).toUpperCase());
                        name.setText(circle.name);
                        break;
                }

                name.setTextColor(name.getResources().getColor(android.R.color.black));
                counter.setText(String.valueOf(circle.counter));
                counter.setVisibility(View.VISIBLE);
                itemView.setOnClickListener( v -> {
                    Circles.getInstance().setSelectedCircle(circle);
                    if (circle.circleType == CircleType.ARCHIVE) {
                        Bundle args = new Bundle();
                        args.putInt("folderId", 1);
                        baseFragment.presentFragment(new DialogsActivity(args));
                    }
                    dismiss();
                });
            }
        }
    }
}

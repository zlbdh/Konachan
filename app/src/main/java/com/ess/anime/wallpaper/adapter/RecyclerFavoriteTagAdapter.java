package com.ess.anime.wallpaper.adapter;

import android.app.Activity;
import android.text.TextUtils;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseViewHolder;
import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.database.FavoriteTagBean;
import com.ess.anime.wallpaper.model.helper.TagOperationHelper;
import com.ess.anime.wallpaper.ui.view.CustomDialog;
import com.ess.anime.wallpaper.utils.TimeFormat;
import com.mixiaoxiao.smoothcompoundbutton.SmoothCheckBox;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class RecyclerFavoriteTagAdapter extends BaseRecyclerEditAdapter<FavoriteTagBean> {

    public RecyclerFavoriteTagAdapter() {
        this(new ArrayList<>());
    }

    public RecyclerFavoriteTagAdapter(@NonNull List<FavoriteTagBean> data) {
        super(R.layout.recyclerview_item_favorite_tag, data);
    }

    @Override
    protected void convert(BaseViewHolder holder, FavoriteTagBean tagBean) {
        // Selection checkbox in edit mode
        holder.setChecked(R.id.cb_choose, isSelected(tagBean));
        holder.setGone(R.id.cb_choose, isEditMode());
        holder.itemView.setOnClickListener(v -> {
            if (isEditMode()) {
                boolean select = !((SmoothCheckBox) holder.getView(R.id.cb_choose)).isChecked();
                if (select) {
                    select(tagBean);
                } else {
                    deselect(tagBean);
                }
                holder.setChecked(R.id.cb_choose, select);
            }
        });

        // Tag text
        holder.setText(R.id.tv_tag, tagBean.getTag());

        // Tag note
        TextView tvAnnotation = holder.getView(R.id.tv_annotation);
        if (TextUtils.isEmpty(tagBean.getAnnotation())) {
            tvAnnotation.setText(R.string.favorite_tag_annotation_empty);
            tvAnnotation.setActivated(false);
        } else {
            tvAnnotation.setText(tagBean.getAnnotation());
            tvAnnotation.setActivated(true);
        }

        // Time the tag was added to favorites
        String date = TimeFormat.dateFormat(tagBean.getFavoriteTime(), "yyyy-MM-dd  HH:mm:ss");
        holder.setText(R.id.tv_favorite_time, mContext.getString(R.string.favorite_tag_favorite_at_time, date));

        // Edit note
        holder.getView(R.id.iv_edit).setOnClickListener(v -> {
            CustomDialog.showEditTagAnnotationDialog(mContext, tagBean.getTag(), true, new CustomDialog.SimpleDialogActionListener() {
                @Override
                public void onPositive() {
                    notifyItemChanged(holder.getLayoutPosition());
                }
            });
        });

        // Search tag
        holder.getView(R.id.iv_search).setOnClickListener(v -> {
            TagOperationHelper.searchTag((Activity) mContext, tagBean.getTag());
        });

        // Copy to clipboard
        holder.getView(R.id.iv_copy).setOnClickListener(v -> {
            TagOperationHelper.copyTagToClipboard((Activity) mContext, tagBean.getTag());
        });

        // Append to clipboard
        holder.getView(R.id.iv_append).setOnClickListener(v -> {
            TagOperationHelper.appendTagToClipboard((Activity) mContext, tagBean.getTag());
        });
    }

    @Override
    protected void convertPayloads(@NonNull BaseViewHolder holder, FavoriteTagBean tagBean, @NonNull List<Object> payloads) {
        super.convertPayloads(holder, tagBean, payloads);
        for (Object payload : payloads) {
            if (payload.equals(TOGGLE_EDIT_MODE)) {
                // Selection checkbox in edit mode
                holder.setChecked(R.id.cb_choose, isSelected(tagBean));
                holder.setGone(R.id.cb_choose, isEditMode());
            }
        }
    }

    @Override
    protected boolean showEditTransitionAnimation() {
        return true;
    }

}

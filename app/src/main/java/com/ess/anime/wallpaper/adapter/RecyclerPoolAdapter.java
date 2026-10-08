package com.ess.anime.wallpaper.adapter;

import android.app.Activity;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.bean.PoolListBean;
import com.ess.anime.wallpaper.glide.MyGlideModule;
import com.ess.anime.wallpaper.utils.SystemUtils;
import com.ess.anime.wallpaper.website.WebsiteManager;

import java.util.List;
import java.util.Map;

public class RecyclerPoolAdapter extends BaseQuickAdapter<PoolListBean, BaseViewHolder> {

    public RecyclerPoolAdapter() {
        super(R.layout.recyclerview_item_pool);
    }

    @Override
    protected void convert(BaseViewHolder holder, PoolListBean poolListBean) {
        //thumbnail
        Map<String, String> headerMap = WebsiteManager.getInstance().getRequestHeaders();
        Object imgUrl = TextUtils.isEmpty(poolListBean.thumbUrl)
                ? poolListBean
                : MyGlideModule.makeGlideUrl(poolListBean.thumbUrl, headerMap);
        Glide.with(mContext)
                .load(imgUrl)
                .placeholder(R.drawable.ic_placeholder_pool)
                .priority(Priority.HIGH)
                .into((ImageView) holder.getView(R.id.iv_pool_thumb));

        //Album name
        holder.setText(R.id.tv_name, poolListBean.name.replace("_", " "));

        //Creator
        String creator = TextUtils.isEmpty(poolListBean.creator)
                ? mContext.getString(R.string.unknown)
                : poolListBean.creator;
        holder.setText(R.id.tv_creator, creator);

        //Image count
        holder.setGone(R.id.tv_post_count, !TextUtils.isEmpty(poolListBean.postCount));
        holder.setText(R.id.tv_post_count, poolListBean.postCount);

        //Creation time
        holder.setText(R.id.tv_create_time, poolListBean.createTime);

        //Upload time
        String update = TextUtils.isEmpty(poolListBean.updateTime)
                ? mContext.getString(R.string.unknown)
                : mContext.getString(R.string.pool_updated_time, poolListBean.updateTime);
        holder.setText(R.id.tv_update_time, update);
    }

    public boolean loadMoreDatas(List<PoolListBean> poolList) {
        return addDatas(mData.size(), poolList);
    }

    public boolean refreshDatas(List<PoolListBean> poolList) {
        return addDatas(0, poolList);
    }

    private boolean addDatas(int position, List<PoolListBean> poolList) {
        synchronized (this) {
            //Remove duplicate thumbList entries caused by new site images during refresh
            poolList.removeAll(mData);
            if (!poolList.isEmpty()) {
                addData(position, poolList);
                preloadThumbnail(poolList);
                return true;
            }
            return false;
        }
    }

    private void preloadThumbnail(List<PoolListBean> poolList) {
        Map<String, String> headerMap = WebsiteManager.getInstance().getRequestHeaders();
        for (PoolListBean poolListBean : poolList) {
            if (SystemUtils.isActivityActive((Activity) mContext)) {
                MyGlideModule.preloadImage(mContext, poolListBean.thumbUrl, headerMap);
            }
        }
    }

}

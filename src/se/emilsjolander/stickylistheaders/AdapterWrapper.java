package se.emilsjolander.stickylistheaders;

import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.ListAdapter;

/**
 * A {@link ListAdapter} which wraps a {@link StickyListHeadersAdapter} and
 * automatically handles wrapping the result of
 * {@link StickyListHeadersAdapter#getView(int, android.view.View, android.view.ViewGroup)}
 * and
 * {@link StickyListHeadersAdapter#getHeaderView(int, android.view.View, android.view.ViewGroup)}
 * appropriately.
 * 
 * @author Jake Wharton (jakewharton@gmail.com)
 */
class AdapterWrapper extends BaseAdapter implements StickyListHeadersAdapter {

	interface OnHeaderClickListener {
		void onHeaderClick(View header, int itemPosition, long headerId);
	}

	static final String LOG_TAG = "AdapterWrapper";
	
	final StickyListHeadersAdapter mDelegate;
	private final List<View> mHeaderCache = new LinkedList<View>();
	private final Context mContext;
	private Drawable mDivider;
	private int mDividerHeight;
	private OnHeaderClickListener mOnHeaderClickListener;
	private DataSetObserver mDataSetObserver = new DataSetObserver() {

		@Override
		public void onInvalidated() {
			mHeaderCache.clear();
			AdapterWrapper.super.notifyDataSetInvalidated();
		}

		@Override
		public void onChanged() {
			AdapterWrapper.super.notifyDataSetChanged();
		}
	};

	AdapterWrapper(Context context, StickyListHeadersAdapter delegate) {
		this.mContext = context;
		this.mDelegate = delegate;
		delegate.registerDataSetObserver(mDataSetObserver);
	}

	void setDivider(Drawable divider, int dividerHeight) {
		this.mDivider = divider;
		this.mDividerHeight = dividerHeight;
		notifyDataSetChanged();
	}

	@Override
	public boolean areAllItemsEnabled() {
		return mDelegate.areAllItemsEnabled();
	}

	@Override
	public boolean isEnabled(int position) {
		return mDelegate.isEnabled(position);
	}

	@Override
	public int getCount() {
		return mDelegate.getCount();
	}

	@Override
	public Object getItem(int position) {
		return mDelegate.getItem(position);
	}

	@Override
	public long getItemId(int position) {
		return mDelegate.getItemId(position);
	}

	@Override
	public boolean hasStableIds() {
		return mDelegate.hasStableIds();
	}

	@Override
	public int getItemViewType(int position) {
		return mDelegate.getItemViewType(position);
	}

	@Override
	public int getViewTypeCount() {
		return mDelegate.getViewTypeCount();
	}

	@Override
	public boolean isEmpty() {
		return mDelegate.isEmpty();
	}

	/**
	 * Will recycle header from {@link WrapperView} if it exists
	 */
	private void recycleHeaderIfExists(WrapperView wv) {
		View header = wv.mHeader;
		if (header != null) {
			// reset the headers visibility when adding it to the cache
			header.setVisibility(View.VISIBLE);
			mHeaderCache.add(header);
			
			L.w(LOG_TAG, "Cache header. size=%d", mHeaderCache.size());
		}
	}
	
	private View popHeader() {
		View header = null;
		if (mHeaderCache.size() > 0) {
			header = mHeaderCache.remove(0);
		}
		
		L.w(LOG_TAG, "Pop   header. header=%s", header);
		
		return header;
	}

	/**
	 * Get a header view. This optionally pulls a header from the supplied
	 * {@link WrapperView} and will also recycle the divider if it exists.
	 */
	private View configureHeader(WrapperView wv, final int position) {
		View header = wv.mHeader == null ? popHeader() : wv.mHeader;
		// 如果header==null，在我们自己写的adapter中就需要重新创建一个header出来
		header = mDelegate.getHeaderView(position, header, wv);
		if (header == null) {
			throw new NullPointerException("Header view must not be null.");
		}
		// if the header isn't clickable, the listselector will be drawn on top of the header
		header.setClickable(true);
		header.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mOnHeaderClickListener != null) {
					long headerId = mDelegate.getHeaderId(position);
					mOnHeaderClickListener.onHeaderClick(v, position, headerId);
				}
			}
		});
		return header;
	}

	/** 
	 * Returns {@code true} if the previous position has the same header ID. 
	 * <p>
	 * 注意：每一个item都有headerId。该方法用来判断当前position处的item的headerId
	 * 和前一个position处的item的headerId是否一样。如果一样，代表当前position处不
	 * 需要显示header，否则需要显示header。
	 * <pre>
	 * 比如：
	 * 0 Aa
	 * 1 Ab
	 * 2 Ac
	 * 3 Ba
	 * 4 Bb
	 * 5 Bc
	 * </pre>
	 * 0位置显示header为A，1位置和2位置就不需要了，3位置和2位置的headerId不一样，需要
	 * 显示一个header。
	 * </p>
	 */
	private boolean previousPositionHasSameHeader(int position) {
		return position != 0 && mDelegate.getHeaderId(position) == mDelegate.getHeaderId(position - 1);
	}

	@Override
	public WrapperView getView(int position, View convertView, ViewGroup parent) {
		WrapperView wv = (convertView == null) ? new WrapperView(mContext) : (WrapperView) convertView;
		View item = mDelegate.getView(position, wv.mItem, parent);
		
		L.v(LOG_TAG, "convertView == null ? %s, wv.mHeader == null ? %s.", convertView == null, wv.mHeader == null);
		
		//===================缓存或者创建新的header（每个item的header）=====================//
		
		View header = null;
		// 当前item自己不需要header
		if (previousPositionHasSameHeader(position)) {
			/*
			 * 这里的真实意义是缓存一个header：
			 * 当一个item被重用时，如果这个item是带有header的item（即header!=null），那么把这个header保存起来。
			 */
			recycleHeaderIfExists(wv);
		} else {
			/*
			 * 这里是创建一个新的header（当需要创建新的header的时候）：
			 * 注意这里创建新的header的时候，首先会判断缓存中有没有header，如果有就把这个header拿出来使用，否则
			 * 就要创建新的。
			 * 该功能的实现依赖于ListView自身的item缓存实现。
			 */
			header = configureHeader(wv, position);
		}
		
		// 由于每个页面可能显示的header的个数不一样，所以缓存的个数也会发生变化，这里不像ListView，每一页显示的
		// item个数都是固定的。缓存中的header就是被创建过的，但是已经不用了的对象。
		
		//===================缓存或者创建新的header（每个item的header）=====================//
		
		if ((item instanceof Checkable) && !(wv instanceof CheckableWrapperView)) {
			// Need to create Checkable subclass of WrapperView for ListView to
			// work correctly
			wv = new CheckableWrapperView(mContext);
		} else if (!(item instanceof Checkable) && (wv instanceof CheckableWrapperView)) {
			wv = new WrapperView(mContext);
		}
		wv.update(item, header, mDivider, mDividerHeight);
		
		L.d(LOG_TAG, "Get view: position=%d, header.visibility=%s.", position, Util.getVisibilityStr(header));
		
		return wv;
	}

	public void setOnHeaderClickListener(OnHeaderClickListener onHeaderClickListener) {
		this.mOnHeaderClickListener = onHeaderClickListener;
	}

	@Override
	public boolean equals(Object o) {
		return mDelegate.equals(o);
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		return ((BaseAdapter) mDelegate).getDropDownView(position, convertView, parent);
	}

	@Override
	public int hashCode() {
		return mDelegate.hashCode();
	}

	@Override
	public void notifyDataSetChanged() {
		((BaseAdapter) mDelegate).notifyDataSetChanged();
	}

	@Override
	public void notifyDataSetInvalidated() {
		((BaseAdapter) mDelegate).notifyDataSetInvalidated();
	}

	@Override
	public String toString() {
		return mDelegate.toString();
	}

	@Override
	public View getHeaderView(int position, View convertView, ViewGroup parent) {
		return mDelegate.getHeaderView(position, convertView, parent);
	}

	@Override
	public long getHeaderId(int position) {
		return mDelegate.getHeaderId(position);
	}

}

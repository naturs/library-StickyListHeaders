package se.emilsjolander.stickylistheaders;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/**
 * 
 * the view that wrapps a divider header and a normal list item. The listview
 * sees this as 1 item
 * 
 * @author Emil Sjölander
 */
public class WrapperView extends ViewGroup {

	View mItem;
	Drawable mDivider;
	int mDividerHeight;
	View mHeader;
	int mItemTop;

	WrapperView(Context c) {
		super(c);
	}

	public boolean hasHeader() {
		return mHeader != null;
	}

	public View getItem() {
		return mItem;
	}

	public View getHeader() {
		return mHeader;
	}

	void update(View item, View header, Drawable divider, int dividerHeight) {

		// every wrapperview must have a list item
		if (item == null) {
			throw new NullPointerException("List view item must not be null.");
		}

		// only remove the current item if it is not the same as the new item.
		// this can happen if wrapping a recycled view
		if (this.mItem != item) { // 如果WrapperView被重用的时候，传进来的item和它自己的item就是一样的
			removeView(this.mItem);
			this.mItem = item;
			
			// XXX:其实下面这段没必要
			final ViewParent parent = item.getParent();
			if (parent != null && parent != this) {
				if (parent instanceof ViewGroup) {
					((ViewGroup) parent).removeView(item);
				}
			}
			
			addView(item);
		}

		
		// same logik as above but for the header
		if (this.mHeader != header) {
			if (this.mHeader != null) {
				removeView(this.mHeader);
			}
			this.mHeader = header;
			if (header != null) {
				/*
				 * 这里能够保证header的parent是null。
				 * 
				 * 1. 如果header是新创建的就不用说了。
				 * 
				 * 2. 如果header是从缓存中拿出来的，当它被加到缓存中的时候，
				 * 代表ListView的那个item（即某一个WrapperView）带有header，
				 * 并且那个item不需要header，所以当它update的时候就传进来
				 * null，根据上面的两个if语句，header就被移除了。
				 * 
				 * 也就是说，header被缓存的时候，它所在的WrapperView就已经
				 * 将它remove掉了。
				 */
				addView(header);
			}
		}

		if (this.mDivider != divider) {
			this.mDivider = divider;
			this.mDividerHeight = dividerHeight;
			invalidate();
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
		int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY);
		int measuredHeight = 0;

		// measure header or divider. when there is a header visible it acts as the divider
		if (mHeader != null) {
			ViewGroup.LayoutParams params = mHeader.getLayoutParams();
			if (params != null && params.height > 0) {
				mHeader.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY));
			} else {
				mHeader.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
			}
			measuredHeight += mHeader.getMeasuredHeight();
		} else if (mDivider != null && mItem.getVisibility() != View.GONE) {
			measuredHeight += mDividerHeight;
		}

		// measure item
		ViewGroup.LayoutParams params = mItem.getLayoutParams();
		// enable hiding listview item,ex. toggle off items in group
		if (mItem.getVisibility() == View.GONE) {
			mItem.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY));
		} else if (params != null && params.height >= 0) {
			mItem.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(params.height, MeasureSpec.EXACTLY));
			measuredHeight += mItem.getMeasuredHeight();
		} else {
			mItem.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
			measuredHeight += mItem.getMeasuredHeight();
		}

		setMeasuredDimension(measuredWidth, measuredHeight);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {

		l = 0;
		t = 0;
		r = getWidth();
		b = getHeight();

		if (mHeader != null) {
			int headerHeight = mHeader.getMeasuredHeight();
			mHeader.layout(l, t, r, headerHeight);
			mItemTop = headerHeight;
			mItem.layout(l, headerHeight, r, b);
		} else if (mDivider != null) {
			mDivider.setBounds(l, t, r, mDividerHeight);
			mItemTop = mDividerHeight;
			mItem.layout(l, mDividerHeight, r, b);
		} else {
			mItemTop = t;
			mItem.layout(l, t, r, b);
		}
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		if (mHeader == null && mDivider != null && mItem.getVisibility() != View.GONE) {
			// Drawable.setBounds() does not seem to work pre-honeycomb. So have
			// to do this instead
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
				canvas.clipRect(0, 0, getWidth(), mDividerHeight);
			}
			mDivider.draw(canvas);
		}
	}
}

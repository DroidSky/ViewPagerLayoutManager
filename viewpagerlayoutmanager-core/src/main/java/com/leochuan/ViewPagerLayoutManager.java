package com.leochuan;

import android.graphics.PointF;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

/**
 * An implementation of {@link RecyclerView.LayoutManager} which behaves like view pager.
 * Please make sure your child view have the same size.
 */

@SuppressWarnings("WeakerAccess")
public abstract class ViewPagerLayoutManager extends RecyclerView.LayoutManager
        implements RecyclerView.SmoothScroller.ScrollVectorProvider {

    public static final int HORIZONTAL = OrientationHelper.HORIZONTAL;

    public static final int VERTICAL = OrientationHelper.VERTICAL;

    protected int mDecoratedMeasurement;

    protected int mDecoratedMeasurementInOther;

    /**
     * Current orientation. Either {@link #HORIZONTAL} or {@link #VERTICAL}
     */
    int mOrientation;

    protected int mSpaceMain;

    protected int mSpaceInOther;

    /**
     * The offset of property which will change while scrolling
     */
    protected float mOffset;

    /**
     * Many calculations are made depending on orientation. To keep it clean, this interface
     * helps {@link LinearLayoutManager} make those decisions.
     * Based on {@link #mOrientation}, an implementation is lazily created in
     * {@link #ensureLayoutState} method.
     */
    protected OrientationHelper mOrientationHelper;

    /**
     * Defines if layout should be calculated from end to start.
     */
    private boolean mReverseLayout = false;

    /**
     * Works the same way as {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}.
     * see {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}
     */
    private boolean mSmoothScrollbarEnabled = true;

    /**
     * When LayoutManager needs to scroll to a position, it sets this variable and requests a
     * layout which will check this variable and re-layout accordingly.
     */
    private int mPendingScrollPosition = NO_POSITION;

    private SavedState mPendingSavedState = null;

    protected float mInterval; //the mInterval of each item's mOffset

    /* package */ OnPageChangeListener onPageChangeListener;

    private boolean mRecycleChildrenOnDetach;

    private boolean mInfinite = false;

    private boolean mEnableBringCenterToFront;

    /**
     * @return the mInterval of each item's mOffset
     */
    protected abstract float setInterval();

    /**
     * You can set up your own properties here or change the exist properties like mSpaceMain and mSpaceInOther
     */
    protected abstract void setUp();

    protected abstract void setItemViewProperty(View itemView, float targetOffset);

    protected float setViewElevation(View itemView, float targetOffset) {
        return 0;
    }

    /**
     * Creates a horizontal ViewPagerLayoutManager
     */
    public ViewPagerLayoutManager() {
        this(HORIZONTAL, false);
    }

    /**
     * @param orientation   Layout orientation. Should be {@link #HORIZONTAL} or {@link #VERTICAL}
     * @param reverseLayout When set to true, layouts from end to start
     */
    public ViewPagerLayoutManager(int orientation, boolean reverseLayout) {
        setOrientation(orientation);
        setReverseLayout(reverseLayout);
        setAutoMeasureEnabled(true);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Returns whether LayoutManager will recycle its children when it is detached from
     * RecyclerView.
     *
     * @return true if LayoutManager will recycle its children when it is detached from
     * RecyclerView.
     */
    public boolean getRecycleChildrenOnDetach() {
        return mRecycleChildrenOnDetach;
    }

    /**
     * Set whether LayoutManager will recycle its children when it is detached from
     * RecyclerView.
     * <p>
     * If you are using a {@link RecyclerView.RecycledViewPool}, it might be a good idea to set
     * this flag to <code>true</code> so that views will be available to other RecyclerViews
     * immediately.
     * <p>
     * Note that, setting this flag will result in a performance drop if RecyclerView
     * is restored.
     *
     * @param recycleChildrenOnDetach Whether children should be recycled in detach or not.
     */
    public void setRecycleChildrenOnDetach(boolean recycleChildrenOnDetach) {
        mRecycleChildrenOnDetach = recycleChildrenOnDetach;
    }

    @Override
    public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);
        if (mRecycleChildrenOnDetach) {
            removeAndRecycleAllViews(recycler);
            recycler.clear();
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        if (mPendingSavedState != null) {
            return new SavedState(mPendingSavedState);
        }
        SavedState savedState = new SavedState();
        savedState.position = getCurrentPositionInternal();
        savedState.isReverseLayout = mReverseLayout;
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            mPendingSavedState = new SavedState((SavedState) state);
            requestLayout();
        }
    }

    /**
     * @return true if {@link #getOrientation()} is {@link #HORIZONTAL}
     */
    @Override
    public boolean canScrollHorizontally() {
        return mOrientation == HORIZONTAL;
    }

    /**
     * @return true if {@link #getOrientation()} is {@link #VERTICAL}
     */
    @Override
    public boolean canScrollVertically() {
        return mOrientation == VERTICAL;
    }

    /**
     * Returns the current orientation of the layout.
     *
     * @return Current orientation,  either {@link #HORIZONTAL} or {@link #VERTICAL}
     * @see #setOrientation(int)
     */
    public int getOrientation() {
        return mOrientation;
    }

    /**
     * Sets the orientation of the layout. {@link ViewPagerLayoutManager}
     * will do its best to keep scroll position.
     *
     * @param orientation {@link #HORIZONTAL} or {@link #VERTICAL}
     */
    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException("invalid orientation:" + orientation);
        }
        assertNotInLayoutOrScroll(null);
        if (orientation == mOrientation) {
            return;
        }
        mOrientation = orientation;
        mOrientationHelper = null;
        requestLayout();
    }

    /**
     * Calculates the view layout order. (e.g. from end to start or start to end)
     * RTL layout support is applied automatically. So if layout is RTL and
     * {@link #getReverseLayout()} is {@code true}, elements will be laid out starting from left.
     */
    private void resolveShouldLayoutReverse() {
        if (mOrientation == HORIZONTAL && getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL) {
            mReverseLayout = !mReverseLayout;
        }
    }

    /**
     * Returns if views are laid out from the opposite direction of the layout.
     *
     * @return If layout is reversed or not.
     * @see #setReverseLayout(boolean)
     */
    public boolean getReverseLayout() {
        return mReverseLayout;
    }

    /**
     * Used to reverse item traversal and layout order.
     * This behaves similar to the layout change for RTL views. When set to true, first item is
     * laid out at the end of the UI, second item is laid out before it etc.
     * <p>
     * For horizontal layouts, it depends on the layout direction.
     * When set to true, If {@link android.support.v7.widget.RecyclerView} is LTR, than it will
     * layout from RTL, if {@link android.support.v7.widget.RecyclerView}} is RTL, it will layout
     * from LTR.
     */
    public void setReverseLayout(boolean reverseLayout) {
        assertNotInLayoutOrScroll(null);
        if (reverseLayout == mReverseLayout) {
            return;
        }
        mReverseLayout = reverseLayout;
        requestLayout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View findViewByPosition(int position) {
        final int childCount = getChildCount();
        if (childCount == 0) {
            return null;
        }
        final int firstChild = getPosition(getChildAt(0));
        final int viewPosition = position - firstChild;
        if (viewPosition >= 0 && viewPosition < childCount) {
            final View child = getChildAt(viewPosition);
            if (getPosition(child) == position) {
                return child; // in pre-layout, this may not match
            }
        }
        // fallback to traversal. This might be necessary in pre-layout.
        return super.findViewByPosition(position);
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext());
        linearSmoothScroller.setTargetPosition(position);
        startSmoothScroll(linearSmoothScroller);
    }

    public PointF computeScrollVectorForPosition(int targetPosition) {
        if (getChildCount() == 0) {
            return null;
        }
        final int firstChildPos = getPosition(getChildAt(0));
        final float direction = targetPosition < firstChildPos == !mReverseLayout ?
                -1 / getDistanceRatio() : 1 / getDistanceRatio();
        if (mOrientation == HORIZONTAL) {
            return new PointF(direction, 0);
        } else {
            return new PointF(0, direction);
        }
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (state.getItemCount() == 0) {
            removeAndRecycleAllViews(recycler);
            mOffset = 0;
            return;
        }

        ensureLayoutState();
        resolveShouldLayoutReverse();

        if (getChildCount() == 0) {
            View scrap = recycler.getViewForPosition(0);
            measureChildWithMargins(scrap, 0, 0);
            mDecoratedMeasurement = mOrientationHelper.getDecoratedMeasurement(scrap);
            mDecoratedMeasurementInOther = mOrientationHelper.getDecoratedMeasurementInOther(scrap);
            mSpaceMain = (mOrientationHelper.getTotalSpace() - mDecoratedMeasurement) / 2;
            mSpaceInOther = (mOrientationHelper.getTotalSpaceInOther() - mDecoratedMeasurementInOther) / 2;
            mInterval = setInterval();
            setUp();
        }

        if (mPendingSavedState != null) {
            mReverseLayout = mPendingSavedState.isReverseLayout;
            mPendingScrollPosition = mPendingSavedState.position;
        }

        if (mPendingScrollPosition != NO_POSITION) {
            mOffset = mReverseLayout ?
                    mPendingScrollPosition * -mInterval : mPendingScrollPosition * mInterval;
        }

        detachAndScrapAttachedViews(recycler);
        layoutItems(recycler, state);
    }

    @Override
    public void onLayoutCompleted(RecyclerView.State state) {
        super.onLayoutCompleted(state);
        mPendingSavedState = null;
        mPendingScrollPosition = NO_POSITION;
    }

    void ensureLayoutState() {
        if (mOrientationHelper == null) {
            mOrientationHelper = OrientationHelper.createOrientationHelper(this, mOrientation);
        }
    }

    private float getProperty(int position) {
        return !mReverseLayout ? position * mInterval : position * -mInterval;
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        removeAllViews();
        mOffset = 0;
    }

    @Override
    public void scrollToPosition(int position) {
        mPendingScrollPosition = position;
        mOffset = mReverseLayout ? position * -mInterval : position * mInterval;
        requestLayout();
    }

    @Override
    public int computeHorizontalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset();
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset();
    }

    @Override
    public int computeHorizontalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent();
    }

    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent();
    }

    @Override
    public int computeHorizontalScrollRange(RecyclerView.State state) {
        return computeScrollRange();
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return computeScrollRange();
    }

    private int computeScrollOffset() {
        if (getChildCount() == 0) {
            return 0;
        }

        if (!mSmoothScrollbarEnabled) {
            return !mReverseLayout ?
                    getCurrentPositionInternal() : getItemCount() - getCurrentPositionInternal() - 1;
        }

        return !mReverseLayout ? (int) mOffset : (int) (Math.abs(getMinOffset()) + mOffset);
    }

    private int computeScrollExtent() {
        if (getChildCount() == 0) {
            return 0;
        }

        if (!mSmoothScrollbarEnabled) {
            return 1;
        }

        return !mReverseLayout ?
                (int) (getMaxOffset() / getItemCount()) : (int) Math.abs(getMinOffset() / getItemCount());
    }

    private int computeScrollRange() {
        if (getChildCount() == 0) {
            return 0;
        }

        if (!mSmoothScrollbarEnabled) {
            return getItemCount();
        }

        return !mReverseLayout ? (int) getMaxOffset() : (int) Math.abs(getMinOffset());
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (mOrientation == VERTICAL) {
            return 0;
        }
        return scrollBy(dx, recycler, state);
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (mOrientation == HORIZONTAL) {
            return 0;
        }
        return scrollBy(dy, recycler, state);
    }

    private int scrollBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0 || dy == 0) {
            return 0;
        }
        ensureLayoutState();
        int willScroll = dy;

        float realDx = dy / getDistanceRatio();
        float targetOffset = mOffset + realDx;

        //handle the boundary
        if (!mInfinite && targetOffset < getMinOffset()) {
            willScroll -= (targetOffset - getMinOffset()) * getDistanceRatio();
        } else if (!mInfinite && targetOffset > getMaxOffset()) {
            willScroll = (int) ((getMaxOffset() - mOffset) * getDistanceRatio());
        }

        realDx = willScroll / getDistanceRatio();

        mOffset += realDx;

        for (int i = 0; i < getChildCount(); i++) {
            final View scrap = getChildAt(i);
            final float delta = propertyChangeWhenScroll(scrap) - realDx;
            layoutScrap(scrap, delta);
        }

        layoutItems(recycler, state);

        return willScroll;
    }

    private void layoutItems(RecyclerView.Recycler recycler,
                             RecyclerView.State state) {
        if (mEnableBringCenterToFront && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            detachAndScrapAttachedViews(recycler);
        } else {
            //remove the views which is out of range
            for (int i = 0; i < getChildCount(); i++) {
                final View view = getChildAt(i);
                final int position = getPosition(view);
                if (removeCondition(getProperty(position) - mOffset)) {
                    removeAndRecycleView(view, recycler);
                }
            }
        }

        final int currentPos = getCurrentPositionInternal();
        final float curOffset = getProperty(currentPos) - mOffset;

        int start = (int) (currentPos - Math.abs(((curOffset - minRemoveOffset()) / mInterval))) - 1;
        int end = (int) (currentPos + Math.abs(((curOffset - maxRemoveOffset()) / mInterval))) + 1;

        if (start < 0 && !mInfinite) start = 0;
        final int itemCount = getItemCount();
        if (end > itemCount && !mInfinite) end = itemCount;

        float lastOrderWeight = Float.MIN_VALUE;
        for (int i = start; i < end; i++) {
            if (!removeCondition(getProperty(i) - mOffset)) {
                int realIndex = i;
                if (i >= itemCount) {
                    realIndex %= itemCount;
                } else if (i < 0) {
                    int delta = (-realIndex) % itemCount;
                    if (delta == 0) delta = itemCount;
                    realIndex = itemCount - delta;
                }
                if (findViewByPosition(i) == null) {
                    final View scrap = recycler.getViewForPosition(realIndex);
                    measureChildWithMargins(scrap, 0, 0);
                    resetViewProperty(scrap);
                    final float targetOffset = getProperty(i) - mOffset;
                    layoutScrap(scrap, targetOffset);
                    final float orderWeight = mEnableBringCenterToFront ? setViewElevation(scrap, targetOffset)
                            : realIndex;
                    if (orderWeight > lastOrderWeight) {
                        addView(scrap);
                    } else {
                        addView(scrap, 0);
                    }
                    lastOrderWeight = orderWeight;
                }
            }
        }

        // handle cycle jump
        if (mInfinite) {
            if (getCurrentPositionInternal() == 0) {
                removeAndRecycleAllViews(recycler);
                internalScrollToPosition(itemCount, recycler, state);
            } else if (getCurrentPositionInternal() == itemCount + 1) {
                removeAndRecycleAllViews(recycler);
                internalScrollToPosition(1, recycler, state);
            }
        }
    }

    private void internalScrollToPosition(int position, RecyclerView.Recycler recycler, RecyclerView.State state) {
        mOffset = mReverseLayout ? position * -mInterval : position * mInterval;
        layoutItems(recycler, state);
    }

    private boolean removeCondition(float targetOffset) {
        return targetOffset > maxRemoveOffset() || targetOffset < minRemoveOffset();
    }

    private void resetViewProperty(View v) {
        v.setRotation(0);
        v.setRotationY(0);
        v.setRotationX(0);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setAlpha(1f);
    }

    private float getMaxOffset() {
        return !mReverseLayout ?
                (mInfinite ? (getItemCount() + 1) : (getItemCount() - 1)) * mInterval : 0;
    }

    private float getMinOffset() {
        return !mReverseLayout ?
                0 : -(mInfinite ? (getItemCount() + 1) : (getItemCount() - 1)) * mInterval;
    }

    private void layoutScrap(View scrap, float targetOffset) {
        final int left = calMainDirection(targetOffset);
        final int top = calOtherDirection(targetOffset);
        if (mOrientation == VERTICAL) {
            layoutDecorated(scrap, mSpaceInOther + left, mSpaceMain + top,
                    mSpaceInOther + left + mDecoratedMeasurementInOther, mSpaceMain + top + mDecoratedMeasurement);
        } else {
            layoutDecorated(scrap, mSpaceMain + left, mSpaceInOther + top,
                    mSpaceMain + left + mDecoratedMeasurement, mSpaceInOther + top + mDecoratedMeasurementInOther);
        }
        setItemViewProperty(scrap, targetOffset);
    }

    protected int calMainDirection(float targetOffset) {
        return mOrientation == VERTICAL ? 0 : (int) targetOffset;
    }

    protected int calOtherDirection(float targetOffset) {
        return mOrientation == VERTICAL ? (int) targetOffset : 0;
    }

    protected float maxRemoveOffset() {
        return mOrientationHelper.getTotalSpace() - mSpaceMain;
    }

    protected float minRemoveOffset() {
        return -mDecoratedMeasurement - mOrientationHelper.getStartAfterPadding() - mSpaceMain;
    }

    protected float propertyChangeWhenScroll(View itemView) {
        if (mOrientation == VERTICAL)
            return itemView.getTop() - mSpaceMain;
        return itemView.getLeft() - mSpaceMain;
    }

    protected float getDistanceRatio() {
        return 1f;
    }

    public int getCurrentPosition() {
        int position = getCurrentPositionInternal();
        if (mInfinite && position > getItemCount()) return position - getItemCount();
        else if (mInfinite && position < 0) return position + getItemCount();
        return position;
    }

    private int getCurrentPositionInternal() {
        return Math.round(Math.abs(mOffset) / mInterval);
    }

    public int getOffsetCenterView() {
        return (int) ((getCurrentPositionInternal() * (!mReverseLayout ?
                mInterval : -mInterval) - mOffset) * getDistanceRatio());
    }

    public void setOnPageChangeListener(OnPageChangeListener onPageChangeListener) {
        this.onPageChangeListener = onPageChangeListener;
    }

    public void setInfinite(boolean enable) {
        assertNotInLayoutOrScroll(null);
        if (enable == mInfinite) {
            return;
        }
        mInfinite = enable;
        requestLayout();
    }

    public boolean getInfinite() {
        return mInfinite;
    }

    /**
     * When smooth scrollbar is enabled, the position and size of the scrollbar thumb is computed
     * based on the number of visible pixels in the visible items. This however assumes that all
     * list items have similar or equal widths or heights (depending on list orientation).
     * If you use a list in which items have different dimensions, the scrollbar will change
     * appearance as the user scrolls through the list. To avoid this issue,  you need to disable
     * this property.
     * <p>
     * When smooth scrollbar is disabled, the position and size of the scrollbar thumb is based
     * solely on the number of items in the adapter and the position of the visible items inside
     * the adapter. This provides a stable scrollbar as the user navigates through a list of items
     * with varying widths / heights.
     *
     * @param enabled Whether or not to enable smooth scrollbar.
     * @see #setSmoothScrollbarEnabled(boolean)
     */
    public void setSmoothScrollbarEnabled(boolean enabled) {
        mSmoothScrollbarEnabled = enabled;
    }

    public void setEnableBringCenterToFront(boolean bringCenterToTop) {
        assertNotInLayoutOrScroll(null);
        if (mEnableBringCenterToFront == bringCenterToTop) {
            return;
        }
        this.mEnableBringCenterToFront = bringCenterToTop;
        requestLayout();
    }

    public boolean getEnableBringCenterToFront() {
        return mEnableBringCenterToFront;
    }

    /**
     * Returns the current state of the smooth scrollbar feature. It is enabled by default.
     *
     * @return True if smooth scrollbar is enabled, false otherwise.
     * @see #setSmoothScrollbarEnabled(boolean)
     */
    public boolean getSmoothScrollbarEnabled() {
        return mSmoothScrollbarEnabled;
    }

    private static class SavedState implements Parcelable {
        int position;
        boolean isReverseLayout;

        SavedState() {

        }

        SavedState(Parcel in) {
            position = in.readInt();
            isReverseLayout = in.readInt() == 1;
        }

        public SavedState(SavedState other) {
            position = other.position;
            isReverseLayout = other.isReverseLayout;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(position);
            dest.writeInt(isReverseLayout ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public interface OnPageChangeListener {
        void onPageSelected(int position);

        void onPageScrollStateChanged(int state);
    }
}

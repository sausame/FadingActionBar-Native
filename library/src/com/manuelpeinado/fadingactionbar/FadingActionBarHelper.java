/*
 * Copyright (C) 2013 Manuel Peinado
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.manuelpeinado.fadingactionbar;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ScrollView;

import com.cyrilmottier.android.translucentactionbar.NotifyingScrollView;

public class FadingActionBarHelper {
    protected static final String TAG = "FadingActionBarHelper";
    private Drawable mActionBarBackgroundDrawable;
    private FrameLayout mHeaderContainer;
    private int mActionBarBackgroundResId;
    private int mHeaderLayoutResId = 0;
    private View mHeaderView;
    private int mContentLayoutResId;
    private View mContentView;
    private ActionBar mActionBar;
    private LayoutInflater mInflater;
    private boolean mLightActionBar;
    private boolean mUseParallax = true;
    private int mLastHeaderHeight = -1;
    private ViewGroup mContentContainer;
    private ViewGroup mScrollView;
    private boolean mFirstGlobalLayoutPerformed;
    private View mMarginView;
    private View mListViewBackgroundView;


    public FadingActionBarHelper actionBarBackground(int drawableResId) {
        mActionBarBackgroundResId = drawableResId;
        return this;
    }

    public FadingActionBarHelper actionBarBackground(Drawable drawable) {
        mActionBarBackgroundDrawable = drawable;
        return this;
    }

    public FadingActionBarHelper headerLayout(int layoutResId) {
        mHeaderLayoutResId = layoutResId;
        return this;
    }

    public FadingActionBarHelper headerView(View view) {
        mHeaderView = view;
        return this;
    }

    public FadingActionBarHelper contentLayout(int layoutResId) {
        mContentLayoutResId = layoutResId;
        return this;
    }

    public FadingActionBarHelper contentView(View view) {
        mContentView = view;
        return this;
    }

    public FadingActionBarHelper lightActionBar(boolean value) {
        mLightActionBar = value;
        return this;
    }

    public FadingActionBarHelper parallax(boolean value) {
        mUseParallax = value;
        return this;
    }

    public View createView(Context context) {
        return createView(LayoutInflater.from(context));
    }

    public View createView(LayoutInflater inflater) {
        //
        // Prepare everything

        mInflater = inflater;
        if (mContentView == null) {
            mContentView = inflater.inflate(mContentLayoutResId, null);
        }
        if (mHeaderView == null && mHeaderLayoutResId != 0) {
            mHeaderView = inflater.inflate(mHeaderLayoutResId, mHeaderContainer, false);
        }

        //
        // See if we are in a ListView or ScrollView scenario

        ListView listView = (ListView) mContentView.findViewById(android.R.id.list);
        View root;
        if (listView != null) {
            root = createListView(listView);
        } else {
            root = createScrollView();
        }
        
        if (mHeaderView != null) {
	        // Use measured height here as an estimate of the header height, later on after the layout is complete 
	        // we'll use the actual height
	        int widthMeasureSpec = MeasureSpec.makeMeasureSpec(LayoutParams.MATCH_PARENT, MeasureSpec.EXACTLY);
	        int heightMeasureSpec = MeasureSpec.makeMeasureSpec(LayoutParams.WRAP_CONTENT, MeasureSpec.EXACTLY);
	        mHeaderView.measure(widthMeasureSpec, heightMeasureSpec);
	        updateHeaderHeight(mHeaderView.getMeasuredHeight());
        }

        root.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
            	if (mHeaderView != null) {
	                int headerHeight = mHeaderContainer.getHeight();
	                if (!mFirstGlobalLayoutPerformed && headerHeight != 0) {
	                    updateHeaderHeight(headerHeight);
	                    mFirstGlobalLayoutPerformed = true;
	                }
            	}
            }
        });
        return root;
    }

    public void initActionBar(Activity activity) {
        mActionBar = getActionBar(activity);
        if (mActionBarBackgroundDrawable == null) {
            mActionBarBackgroundDrawable = activity.getResources().getDrawable(mActionBarBackgroundResId);
        }
        mActionBar.setBackgroundDrawable(mActionBarBackgroundDrawable);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
            mActionBarBackgroundDrawable.setCallback(mDrawableCallback);
        }
        
        if (mHeaderView != null) {
        	mActionBarBackgroundDrawable.setAlpha(0);
        } else {
            mActionBarBackgroundDrawable.setAlpha(255);
        }
    }

    protected ActionBar getActionBar(Activity activity) {
        return activity.getActionBar();
    }

    private Drawable.Callback mDrawableCallback = new Drawable.Callback() {
        @Override
        public void invalidateDrawable(Drawable who) {
            mActionBar.setBackgroundDrawable(who);
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
        }
    };

    private View createScrollView() {
        mScrollView = (ViewGroup) mInflater.inflate(R.layout.fab__scrollview_container, null);

        NotifyingScrollView scrollView = (NotifyingScrollView) mScrollView.findViewById(R.id.fab__scroll_view);
        scrollView.setOnScrollChangedListener(mOnScrollChangedListener);

        mContentContainer = (ViewGroup) mScrollView.findViewById(R.id.fab__container);
        mContentContainer.addView(mContentView);
        
        if (mHeaderView != null) {
	        mHeaderContainer = (FrameLayout) mScrollView.findViewById(R.id.fab__header_container);
	        initializeGradient(mHeaderContainer);
        
        	mHeaderContainer.addView(mHeaderView, 0);
        	mMarginView = mContentContainer.findViewById(R.id.fab__content_top_margin);
        }

        return mScrollView;
    }

    private NotifyingScrollView.OnScrollChangedListener mOnScrollChangedListener = new NotifyingScrollView.OnScrollChangedListener() {
        public void onScrollChanged(ScrollView who, int l, int t, int oldl, int oldt) {
        	if (mHeaderView != null) {
        		onNewScroll(t);
        	}
        }
    };

    private View createListView(ListView listView) {
        mContentContainer = (ViewGroup) mInflater.inflate(R.layout.fab__listview_container, null);
        mContentContainer.addView(mContentView);

        if (mHeaderView != null) {
	        mHeaderContainer = (FrameLayout) mContentContainer.findViewById(R.id.fab__header_container);
	        initializeGradient(mHeaderContainer);

        	mHeaderContainer.addView(mHeaderView, 0);

	        mMarginView = new View(listView.getContext());
	        mMarginView.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, 0));
	        listView.addHeaderView(mMarginView, null, false);
        }
        
        // Make the background as high as the screen so that it fills regardless of the amount of scroll. 
        mListViewBackgroundView = mContentContainer.findViewById(R.id.fab__listview_background);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mListViewBackgroundView.getLayoutParams();
        params.height = Utils.getDisplayHeight(listView.getContext());
        mListViewBackgroundView.setLayoutParams(params);

        listView.setOnScrollListener(mOnScrollListener);
        return mContentContainer;
    }
    

    private OnScrollListener mOnScrollListener = new OnScrollListener() {
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        	if (mHeaderView != null) {
	            View topChild = view.getChildAt(0);	            
	            onNewScroll(topChild == mMarginView);
        	} else {
        		onNewScroll(firstVisibleItem, visibleItemCount, totalItemCount);        		
        	}
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }
    };

    private void onNewScroll(boolean isMarginViewExist) {
        if (mActionBar == null) {
            return;
        }
        
        int currentHeaderHeight = mHeaderContainer.getHeight();
        if (currentHeaderHeight != mLastHeaderHeight) {
            updateHeaderHeight(currentHeaderHeight);
        }

        int scrollPosition;        
        if (isMarginViewExist) {
        	scrollPosition = -1 * mMarginView.getTop();
        } else {
        	scrollPosition = mHeaderContainer.getHeight();
        }

        int headerHeight = currentHeaderHeight - mActionBar.getHeight();
        float ratio = (float) Math.min(Math.max(scrollPosition, 0), headerHeight) / headerHeight;
        int newAlpha = (int) (ratio * 255);
        mActionBarBackgroundDrawable.setAlpha(newAlpha);

        if (isMarginViewExist) {
        	addParallaxEffect();
        } else {
        	hideHeader();
        }
    }
    
    private void onNewScroll(int scrollPosition) {
        if (mActionBar == null) {
            return;
        }
        
        int currentHeaderHeight = mHeaderContainer.getHeight();
        if (currentHeaderHeight != mLastHeaderHeight) {
            updateHeaderHeight(currentHeaderHeight);
        }

        int headerHeight = currentHeaderHeight - mActionBar.getHeight();
        float ratio = (float) Math.min(Math.max(scrollPosition, 0), headerHeight) / headerHeight;
        int newAlpha = (int) (ratio * 255);
        mActionBarBackgroundDrawable.setAlpha(newAlpha);

        addParallaxEffect(scrollPosition);
    }

	private void addParallaxEffect() {
		int offset = mMarginView.getBottom() - mListViewBackgroundView.getTop();		
		if (offset != 0) mListViewBackgroundView.offsetTopAndBottom(offset);

		if (mUseParallax) offset = mMarginView.getTop() / 2 - mHeaderContainer.getTop();
		if (offset != 0) mHeaderContainer.offsetTopAndBottom(offset);
	}

	private void hideHeader() {
		int offset = -1 * mListViewBackgroundView.getTop();
		if (offset != 0) mListViewBackgroundView.offsetTopAndBottom(offset);
		
		offset = -1 * mHeaderContainer.getHeight();
		if (mUseParallax) offset /= 2;
		
		offset -= mHeaderContainer.getTop();
		if (offset != 0) mHeaderContainer.offsetTopAndBottom(offset);		
	}
	
	private void addParallaxEffect(int scrollPosition) {
		int offset = Math.abs(scrollPosition); // Sometimes, it may be a negative number.
		if (mUseParallax) {
			offset /= 2;
		}

		offset = Math.abs(mHeaderContainer.getTop()) - offset;
		mHeaderContainer.offsetTopAndBottom(offset);
	}

    private void updateHeaderHeight(int headerHeight) {
    	if (mMarginView != null) {
	        ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) mMarginView.getLayoutParams();
	        params.height = headerHeight;
	        mMarginView.setLayoutParams(params);
    	}
        if (mListViewBackgroundView != null) {
            FrameLayout.LayoutParams params2 = (FrameLayout.LayoutParams) mListViewBackgroundView.getLayoutParams();
            params2.topMargin = headerHeight;
            mListViewBackgroundView.setLayoutParams(params2);
        }
        mLastHeaderHeight = headerHeight;
    }
    
    private void onNewScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (mActionBar == null) {
            return;
        }

        float ratio = (float) (firstVisibleItem + visibleItemCount) / totalItemCount;
        int newAlpha = (int) (ratio * 255);
        mActionBarBackgroundDrawable.setAlpha(newAlpha);
    }
    
    private void initializeGradient(ViewGroup headerContainer) {
        View gradientView = headerContainer.findViewById(R.id.fab__gradient);
        int gradient = R.drawable.fab__gradient;
        if (mLightActionBar) {
            gradient = R.drawable.fab__gradient_light;
        }
        gradientView.setBackgroundResource(gradient);
    }
}

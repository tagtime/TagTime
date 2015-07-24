package bsoule.tagtime;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;

public class TagToggle extends Button {

	private boolean selected;
	private long tagId;
	
	public TagToggle(Context context) {
		super(context);
		setChecked(false);
		//setTextSize(24);
	}
	public void setTId(long l) {
		tagId = l;
	}
	public long getTId() {
		return tagId;
	}
	
	public TagToggle(Context context, String tag, long id, boolean defOn) {
		super(context);
		setChecked(defOn);
		setText(tag);
		tagId = id;
		LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
		setLayoutParams(p);
		setTextSize(24);
	}
	
	public TagToggle(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setChecked(false);
	}
	public TagToggle(Context context, AttributeSet attrs) {
		super(context, attrs);
		setChecked(false);
	}
	
	@Override
	public boolean performClick() {
		setChecked(!selected);
		return super.performClick();
	}

	private Resources mRes = getResources();
	
	public void setChecked(boolean state) {
		selected = state;
		if (selected) {
			setBackgroundResource(R.drawable.tagbuttononbmp);
			setTextColor(mRes.getColor(R.color.tag_selected_text));
		} else {
			setBackgroundResource(R.drawable.tagbuttonoffpng);
			setTextColor(mRes.getColor(R.color.tag_unselected_text));
		}
	}
	
	public boolean isSelected() {
		return selected;
	}
}

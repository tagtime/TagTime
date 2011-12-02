package bsoule.tagtime;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;

public class TagToggle extends Button {

	private boolean selected;
	private long tagId;
	private PingsDbAdapter mdb;
	
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
		setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
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

	public void setChecked(boolean state) {
		selected = state;
		if (selected) {
			setBackgroundResource(R.drawable.tagbuttononbmp);
		} else {
			setBackgroundResource(R.drawable.tagbuttonoffpng);
		}
	}
	
	public boolean isSelected() {
		return selected;
	}
	
	
}

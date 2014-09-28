package se.emilsjolander.stickylistheaders;

import android.view.View;

public class Util {
	public static String getVisibilityStr(int visibility) {
		String str;
		switch (visibility) {
		case View.VISIBLE:
			str = "VISIBLE";
			break;
			
		case View.INVISIBLE:
			str = "INVISIBLE";
			break;
			
		case View.GONE:
			str = "GONE";
			break;
			
			default:
				str = null;
		}
		return str;
	}
	
	public static String getVisibilityStr(View v) {
		if (v == null) return null;
		return getVisibilityStr(v.getVisibility());
	}
}

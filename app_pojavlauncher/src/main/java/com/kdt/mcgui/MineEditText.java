package com.kdt.mcgui;

import android.content.*;
import android.util.*;
import android.graphics.*;
import android.widget.EditText;

public class MineEditText extends androidx.appcompat.widget.AppCompatEditText {
	public MineEditText(Context ctx) {
		super(ctx);
		init();
	}

	public MineEditText(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		// В светлой теме тёмный фон поля ввода нечитаем
		boolean light = net.kdt.pojavlaunch.prefs.LauncherPreferences.isLightTheme(getContext());
		setBackgroundColor(light ? Color.parseColor("#FFFFFF") : Color.parseColor("#131313"));
		setPadding(5, 5, 5, 5);
	}
}

package org.mospi.momlappviewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.mospi.moml.framework.pub.core.CallContext;
import org.mospi.moml.framework.pub.core.MOMLView;
import org.mospi.moml.framework.pub.core.OnLogListener;
import org.mospi.moml.framework.pub.ui.MOMLUIContainer;
import org.mospi.moml.framework.pub.ui.MOMLUIFrameLayout;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LogView extends FrameLayout {

	private MOMLView momlView;

	private Handler mainHandler = new Handler();
	private Thread logThread;
	private ArrayList<String> systemLogLines = new ArrayList<String>();
	private TextView logView;

	enum WindowPosition {
		TOP, BOTTOM, FULL,
	}

	static WindowPosition windowPosition = WindowPosition.BOTTOM;
	static boolean isVisibleTime = false;
	static boolean isVisibleTag = false;
	String type = "application";
	Button typeButton;
	Button sourceButton;
	final static int MAX_LOG_COUNT = 200;
	
	static LogView currentLogView;

	public LogView(MOMLView momlView) {
		super(momlView.getContext());
		this.momlView = momlView;
		loadSettings();
	}

	public void destroy() {
		momlView.removeView(this);
		momlView = null;
		logView = null;
		logThread = null;
		if (currentLogView == this)
			currentLogView = null;

	}
	
	public static LogView getCurrentLogView() {
		return currentLogView;
	}
	
	protected void onClose() {
		destroy();
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		if (changed && (bottom - top) != getLogViewHeight()) {
			layoutWindowPosition(windowPosition);
		}
	}

	boolean disableTouchLog = true;

	float btnTextSize = 10;
	float logTextSize = 9;

	@SuppressWarnings("deprecation")
	private Button createButton(String title) {
		Button btn = new Button(getContext());
		btn.setText(title);
		btn.setTextColor(Color.WHITE);
		btn.setTextSize(btnTextSize);
		// btn.setBackgroundColor(Color.argb(0xe0, 0x00, 0x80, 0xff));
		int paddingLeft = btn.getPaddingLeft();
		int paddingTop = btn.getPaddingTop();
		int paddingRight = btn.getPaddingRight();
		int paddingBottom = btn.getPaddingBottom();
		btn.setPadding(paddingLeft / 2, paddingTop, paddingRight / 2, paddingBottom);
		StateListDrawable stateListDrawable = new StateListDrawable();
		stateListDrawable.addState(new int[] { android.R.attr.state_pressed }, new ColorDrawable(Color.argb(0xe0, 0x00, 0x80, 0x00)));
		stateListDrawable.addState(new int[] {}, new ColorDrawable(Color.argb(0xe0, 0x00, 0x80, 0xff)));

		btn.setBackgroundDrawable(stateListDrawable);
		return btn;
	}

	private void scrollToBottom() {
		if (logView == null)
			return;
		final int scrollAmount = logView.getLayout().getLineTop(logView.getLineCount()) - getHeight();
		// if there is no need to scroll, scrollAmount will be <=0
		if (scrollAmount > 0) {
			logView.scrollTo(0, scrollAmount);
		}

	}

	public void show() {
		currentLogView = this;
		logView = new TextView(getContext()) {
			{
				setVerticalScrollBarEnabled(true);
				setHorizontalScrollBarEnabled(true);
				setHorizontallyScrolling(true);
				setMovementMethod(ScrollingMovementMethod.getInstance());
				setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
				//setTypeface(Typeface.MONOSPACE);

				// Force scrollbars to be displayed.
				TypedArray a = this.getContext().getTheme().obtainStyledAttributes(new int[0]);
				try {
					// initializeScrollbars(TypedArray)
					Method initializeScrollbars = android.view.View.class.getDeclaredMethod("initializeScrollbars", TypedArray.class);
					initializeScrollbars.invoke(this, a);
				} catch (NoSuchMethodException e) {
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
				a.recycle();

			}

			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
				super.onLayout(changed, left, top, right, bottom);
				if (changed) {
					if (sourceFileIndex < 0) {
						scrollToBottom();
					}
				}
			}
		};
		LinearLayout buttons = new LinearLayout(getContext());
		buttons.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
		LinearLayout.LayoutParams lp;

		Button verticalPosition = createButton("T/B");
		buttons.addView(verticalPosition);
		verticalPosition.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				layoutWindowPosition(getNextWindowPosition());
			}
		});

		typeButton = createButton(getTypeLabel());
		lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.leftMargin = 2;
		buttons.addView(typeButton, lp);
		typeButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				changeType();
			}
		});

		sourceButton = createButton("source");
		lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.leftMargin = 2;
		buttons.addView(sourceButton, lp);
		sourceButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showSource();
			}
		});

		Button time = createButton("time");
		lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.leftMargin = 2;
		buttons.addView(time, lp);
		time.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setVisibleTime(!isVisibleTime);
			}
		});

		Button tag = createButton("tag");
		lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.leftMargin = 2;
		buttons.addView(tag, lp);
		tag.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setVisibleTag(!isVisibleTag);
			}
		});
		
		Button copyButton = createButton("copy");
		lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.leftMargin = 2;
		buttons.addView(copyButton, lp);
		copyButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				copy();
			}
		});
		
		Button clearButton = createButton("clear");
		lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.leftMargin = 2;
		buttons.addView(clearButton, lp);
		clearButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				clear();
			}
		});

		logView.setBackgroundColor(Color.argb(192, 0, 0, 0));
		logView.setTextColor(Color.argb(255, 128, 255, 128));
		logView.setTextSize(logTextSize);

		logView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
		addView(logView);

		addView(buttons);

		Button close = createButton(" X ");
		lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		close.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onClose();
			}
		});
		addView(close, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP));

		momlView.addView(this);

		layoutWindowPosition(windowPosition);

		if (logThread == null) {
			logThread = new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						Process process = Runtime.getRuntime().exec("logcat -v time");
						BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

						String line = "";
						while ((line = bufferedReader.readLine()) != null && logThread != null) {
							// if (line.indexOf("GC_FOR_ALLOC") >= 0)
							// continue;
							//
							// if (line.length() < 20 || line.charAt(19) != 'D')
							// continue;

							if (disableTouchLog) {
								String lcLine = line.toLowerCase(Locale.US);
								if (lcLine.indexOf("moml") < 0) {
									if (lcLine.indexOf("touch") >= 0)
										continue;
								}
							}

							synchronized (systemLogLines) {
								systemLogLines.add(line);

								while (systemLogLines.size() > MAX_LOG_COUNT) {
									systemLogLines.remove(0);
								}
							}

							updateLog();
						}
						logThread = null;
						
						synchronized (systemLogLines) {
							systemLogLines.add("can not read system log.");
							if (MainActivity.isBasicMode)
								systemLogLines.add("system log is not available in \"basic\" version.\nPlease use \"MOML Application Viewer (for developer)\".");

							while (systemLogLines.size() > MAX_LOG_COUNT) {
								systemLogLines.remove(0);
							}
						}

						// Log.d("LOG", "readLine null");
					} catch (IOException e) {
						e.printStackTrace();
					}

				}
			});
			logThread.start();
		}
	}

	private boolean isUpdatingLog;

	private void updateLog() {
		if (!isUpdatingLog) {
			isUpdatingLog = true;

			mainHandler.postDelayed(new Runnable() {

				@Override
				public void run() {
					printLogLines();

					isUpdatingLog = false;
				}
			}, 100);

		}

	}
	
	public void notifyNewLog() {
		// if logThread exists, updateLog is called by logThread. no need to call here.
		if (logThread == null)
			updateLog();
	}


	private void printLogLines() {
		if (logView != null && !sourceViewMode) {
			StringBuilder log = new StringBuilder();
			synchronized (systemLogLines) {

				ArrayList<String> logLines = null;

				if (type.equals("application"))
					logLines = applicationLogLines;
				if (type.equals("device"))
					logLines = deviceLogLines;
				if (type.equals("system"))
					logLines = systemLogLines;
				int size = logLines.size();
				{
					for (int i = 0; i < MAX_LOG_COUNT - size; ++i)
						log.append("\n");
				}
				

				for (int i = 0; i < size; ++i) {
					String line = logLines.get(i);
					
					// check format:  08-29 15:25:55.005 E/ 
					if (line.length() > 21 && line.charAt(2) == '-' && line.charAt(5) == ' ' && line.charAt(8) == ':' && line.charAt(11) == ':') {

						line = line.substring(6); // remove date
						line = line.substring(0, 13) + line.substring(15); // remove priority /
						int open = line.indexOf('(');
						int close = line.indexOf(')');

						if (open >= 0 && close >= 0 && open < close) {
							//String tag = line.substring(13, open);

							line = line.substring(0, open) + line.substring(close + 1);

							if (!isVisibleTag) {
								line = line.substring(0, 13) + line.substring(open + 2);
							}

						}

						if (!isVisibleTime)
							line = line.substring(13);
					}
					final int maxChars = 1024;
					if (line.length() > maxChars) {
						line = line.substring(0, maxChars - 200) + "... line is too long..." + line.substring(line.length() - 100);  
					}
					log.append("\n" + line);
				}
			}

			if (logView.getLayout() != null) {
				String oldText = logView.getText().toString();
				String newText = log.toString();
				if (!oldText.equals(newText)) {
					int sx = logView.getScrollX();
					int sy = logView.getScrollY();
					int bottom = logView.getLayout().getLineTop(logView.getLineCount());
					boolean bottomReached = (sy >= bottom - logView.getHeight());
					logView.setText(newText);
					final int scrollAmount = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
					// if there is no need to scroll, scrollAmount will be <=0
					if (bottomReached && scrollAmount > 0) {
						logView.scrollTo(sx, scrollAmount);
					} else {
						logView.scrollTo(sx, sy);
					}
				}
			}
		}
	}

	private int getLogViewHeight() {
		return momlView.getHeight() * 2 / 5;
	}

	public void layoutWindowPosition(WindowPosition wp) {
		windowPosition = wp;

		int gravity = Gravity.LEFT;
		int height;

		if (wp == WindowPosition.BOTTOM) {
			gravity |= Gravity.BOTTOM;
			height = getLogViewHeight();
		} else if (wp == WindowPosition.TOP) {
			gravity |= Gravity.TOP;
			height = getLogViewHeight();
		} else {
			gravity |= Gravity.TOP;
			height = momlView.getHeight();
		}

		FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, height, gravity);
		setLayoutParams(p);
		
		
		if (logView != null && logView.getLayout() != null) {
			if (sourceViewMode) {
				logView.scrollTo(0, 0);
			} else {
				int sx = logView.getScrollX();
				int sy = logView.getScrollY();
				int bottom = logView.getLayout().getLineTop(logView.getLineCount());
				boolean bottomReached = (sy >= bottom - logView.getHeight());
				final int scrollAmount = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
				// if there is no need to scroll, scrollAmount will be <=0
				if (bottomReached) {
					logView.scrollTo(sx, scrollAmount);
				} else {
					logView.scrollTo(sx, sy);
				}
			}
		}

	}

	private WindowPosition getNextWindowPosition() {
		if (windowPosition == WindowPosition.BOTTOM) {
			return WindowPosition.TOP;
		} else if (windowPosition == WindowPosition.TOP) {
			return WindowPosition.FULL;
		} else {
			return WindowPosition.BOTTOM;
		}

	}

	public void setVisibleTime(boolean visible) {
		isVisibleTime = visible;
		printLogLines();
	}

	public void setVisibleTag(boolean visible) {
		isVisibleTag = visible;
		printLogLines();
	}

	public String getTypeLabel() {
		if (type.equals("application")) {
			return "application.log";
		} else if (type.equals("device")) {
			return "device.log";
		} else if (type.equals("system")) {
			return "system log";
		}
		return "unknown";

	}

	public void changeType() {
		if (sourceViewMode) {
			sourceViewMode = false;
		} else {
			if (type.equals("application")) {
				type = "device";
			} else if (type.equals("device")) {
				type = "system";
			} else if (type.equals("system")) {
				type = "application";
			}
		}
		typeButton.setText(getTypeLabel());
		printLogLines();
		scrollToBottom();
		// final int scrollAmount = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
		// // if there is no need to scroll, scrollAmount will be <=0
		// if (scrollAmount > 0) {
		// logView.scrollTo(0, scrollAmount);
		// } else {
		// logView.scrollTo(0, 0);
		// }
		saveSettings();
	}

	private final static String PREF_MOML_APP_VIEWER = "PREF_MOML_APP_VIEWER";
	private final static String PREF_KEY_CONSOLE_TYPE = "CONSOLE_TYPE";

	private void loadSettings() {
		try {
			SharedPreferences sp = getContext().getSharedPreferences(PREF_MOML_APP_VIEWER, Activity.MODE_PRIVATE);

			String type = sp.getString(PREF_KEY_CONSOLE_TYPE, this.type);

			if (type.equals("application") || type.equals("device") || type.equals("system")) {
				this.type = type;
			}
			
			return;
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.type = "application";
	}
	
	private void saveSettings() {
		try {
			SharedPreferences sp = getContext().getSharedPreferences(PREF_MOML_APP_VIEWER, Activity.MODE_PRIVATE);

			SharedPreferences.Editor editor = sp.edit();
			editor.putString(PREF_KEY_CONSOLE_TYPE, type);
			editor.commit();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	private static ArrayList<String> applicationLogLines = new ArrayList<String>();
	private static ArrayList<String> deviceLogLines = new ArrayList<String>();

	public static OnLogListener momlLogListener = new OnLogListener() {

		@Override
		public void onLog(String type, String tag, String msg) {
			ArrayList<String> logLines = null;

			if (type.equals("application"))
				logLines = applicationLogLines;
			if (type.equals("device"))
				logLines = deviceLogLines;

			if (logLines != null) {
				Date date = new Date();
				SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
				String dateStr = sdf.format(date);

				String line = dateStr + " " + "D/" + tag + "( 0): " + msg;
				logLines.add(line);
				while (logLines.size() > MAX_LOG_COUNT) {
					logLines.remove(0);
				}
				
				LogView currentLogView = LogView.getCurrentLogView();
				if (currentLogView != null)
					currentLogView.notifyNewLog();
				
			}
		}
	};
	
	public static void clearLogs() {
		applicationLogLines.clear();
		deviceLogLines.clear();
	}

	int sourceFileIndex = -1;
	boolean sourceViewMode = false;

	private ArrayList<MOMLUIContainer> containers = new ArrayList<MOMLUIContainer>();
	private void showSource() {
		sourceViewMode = true;
		ArrayList<MOMLUIContainer> containers = new ArrayList<MOMLUIContainer>();

		addContainer(momlView.getRootContainer(), containers);
		
		if (containers.equals(this.containers)) {
			sourceFileIndex--;
			if (sourceFileIndex < 0)
				sourceFileIndex = containers.size() - 1;
		} else {
			this.containers = containers;
			sourceFileIndex = containers.size() - 1;
		}

		if (containers.size() == 0) {
			String text = "\n\n\n No source file found.\n\n";
			logView.setText(text);
			logView.scrollTo(0, 0);
			
			return;
		}

		MOMLUIContainer container = containers.get(sourceFileIndex);
		String sourceFile = container.getDocumentUrl();

		String source = container.getAttrValue("src");
		
		if (!source.startsWith("<"))
			source = fileRead(sourceFile);
		
		source = source.replace("\t", "    ");
		
		String sourceFileList = "";
		
		sourceFileList = "\u25cf [" + (sourceFileIndex + 1) + "/" + containers.size() + "] "  + sourceFile;

		String text = "\n\n\n" + sourceFileList + "\n\n";
		text += addLineNumber(source);

		logView.setText(text);
		logView.scrollTo(0, 0);
	}

	private String fileRead(String sourceFile) {
		try {
			Object fileObject = momlView.getRootContainer().getMomlContext().getObjectManager().findObject("file");
			if (fileObject instanceof org.mospi.moml.framework.pub.object.File) {
				org.mospi.moml.framework.pub.object.File momlFile = (org.mospi.moml.framework.pub.object.File) fileObject;
				CallContext callContext = new CallContext(momlView.getRootContainer());
				return momlFile.read(callContext, sourceFile);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return "file read error";
	}

	private String addLineNumber(String text) {

		String result = text.replace("\r", "");

		String strings[] = result.split("\n");
		int i;

		result = "";
		for (i = 0; i < strings.length; ++i) {
			String lineNumber = String.format(Locale.US, "%4d: ", i + 1);
			result += lineNumber + strings[i] + "\n";
		}
		return result;
	}

	private void addContainer(MOMLUIFrameLayout window, ArrayList<MOMLUIContainer> containers) {
		try {
			if (window instanceof MOMLUIContainer) {
				// add if not exist
				MOMLUIContainer container = (MOMLUIContainer) window;
				String documentUrl = container.getDocumentUrl();
				int count = containers.size();
				int i;
				for (i = 0; i < count; ++i) {
					if (containers.get(i).getDocumentUrl().equals(documentUrl))
						break;
				}
				
				if (i == count)
					containers.add(container);
			}
			@SuppressWarnings("unchecked")
			ArrayList<MOMLUIFrameLayout> childViews = (ArrayList<MOMLUIFrameLayout>) window.childViews;
			int count = childViews.size();
			int i;
			for (i = 0; i < count; ++i) {
				MOMLUIFrameLayout child = childViews.get(i);

				if (child.getVisibility() == View.VISIBLE) {
					addContainer(child, containers);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressLint({ "NewApi", "NewApi", "NewApi", "NewApi" })
	@SuppressWarnings("deprecation")
	@TargetApi(11)
	private void copy() {
		if (logView != null) {
			String label = "MOML App Viewer";
			String text = logView.getText().toString();
			int sdk = android.os.Build.VERSION.SDK_INT;
			if (sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
				android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
				clipboard.setText(text);
			} else {
				android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
				android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
				clipboard.setPrimaryClip(clip);
			}
		}
	}
	
	private void clear() {
		if (sourceViewMode) {
			logView.setText("");
			logView.scrollTo(0, 0);
//		} if (type.equals("system") {
		} else {
			ArrayList<String> logLines = null;
			if (type.equals("application"))
				logLines = applicationLogLines;
			if (type.equals("device"))
				logLines = deviceLogLines;
			else if (type.equals("system"))
				logLines = systemLogLines;
			if (logLines != null) {
				logLines.clear();
				updateLog();
			}
		}
		
	}
}

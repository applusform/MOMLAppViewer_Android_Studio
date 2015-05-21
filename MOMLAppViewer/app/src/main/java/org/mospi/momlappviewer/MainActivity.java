package org.mospi.momlappviewer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

import org.mospi.momlappviewer.R;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {

	EditText editUrl;
	ListView listView;
	
	ArrayList<String> urls = new ArrayList<String>();
	SharedPreferences prefs;
	
	static boolean isBasicMode;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Detects application mode
		if (getPackageName().equals("org.mospi.momlappviewer.basic"))
			isBasicMode = true;
		
		String edition = "";
		
		if (isBasicMode)
			edition = "(basic)";
		else
			edition = "(devel)";
		
		// application title
		try {
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
	        setTitle(getTitle() + " " + info.versionName + " " + edition);
		} catch (Exception e) {
			e.printStackTrace();
		}

		Button buttonGo = (Button)findViewById(R.id.buttonGo);
		editUrl = (EditText)findViewById(R.id.editUrl);
		listView = (ListView)findViewById(R.id.listViewUrls);
		prefs = getSharedPreferences("org.mospi.momlappviewer", Context.MODE_PRIVATE);
		loadUrl();
		listView.invalidateViews();
		editUrl.setText(urls.get(0));
		
		createEditClearButton(editUrl);
		
		buttonGo.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String url = editUrl.getText().toString();
				
				openUrl(url, false);
			}
		});

		listView.setAdapter(new BaseAdapter() {
			
			private int d2p(float dp) {
				return (int)(dp * getResources().getDisplayMetrics().density);
			}


			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				// TODO Auto-generated method stub
				TextView textView;
				if (convertView == null) {
					textView = new TextView(MainActivity.this);
					textView.setHeight(d2p(50));
				} else {
					textView = (TextView)convertView;
				}
				textView.setText(urls.get(position));
				return textView;
			}
			
			@Override
			public long getItemId(int position) {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public Object getItem(int position) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public int getCount() {
				// TODO Auto-generated method stub
				return urls.size();
			}
		});
		
		listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            	editUrl.setText(urls.get(position));
            }
         });
		
		listView.setOnItemLongClickListener(new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
            	
            	final int deleteIndex = position;
            	AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("Delete url")
                .setMessage(urls.get(position))
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    	urls.remove(deleteIndex);
                    	saveUrl();
                    	listView.invalidateViews();
                    }
                })
                 .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    	//
                    }
                })
                .create();
            	
            	dialog.show();
            	
                return true;
            }
		});
		
		// URI Scheme handling
		Intent intent = getIntent();
    	Uri uri = intent.getData();
    	
    	if (uri != null) {
    		// Log.d("MOMLAppViewer", uri.toString());
    		if (uri.getScheme().equals("momlappviewer")) {
    			String address = uri.getSchemeSpecificPart();
    			uriSchemeConfirmUrl(address);
    		}
    		else if (uri.getScheme().equals("file")) {
    			String address = uri.getSchemeSpecificPart();
    			while(address.charAt(0) == '/')
    				address = address.substring(1);
    				
    			uriSchemeConfirmUrl("storage:/" + address);
    		}
    	}
	}

	void uriSchemeConfirmUrl(String url) {
		if (isExistUrl(url)) {
			openUrl(url, true);
		} else {
			final String finalUrl = url;
			
			TextView msgView = new TextView(this);
			msgView.setPadding(10, 5, 10, 5);
			msgView.setText(Html.fromHtml("Do you trust:<br/>\n<b><font color='#0080ff'>" + url + "</font></b><br/>\n<br/>\nIf you don't trust this site, select <b>[No]</b> to exit."));
			
			new AlertDialog.Builder(MainActivity.this)
	        .setTitle("New URL confirm")
	        .setView(msgView)
	        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	            public void onClick(DialogInterface dialog, int whichButton) {
	            	openUrl(finalUrl, true);
	            }
	        })
	         .setNegativeButton("No", new DialogInterface.OnClickListener() {
	            public void onClick(DialogInterface dialog, int whichButton) {
	            	MainActivity.this.finish();
	            }
	        }).setCancelable(false).show();
		}
	}

	
	void openUrl(String url, boolean isUriSchemeOpen) {
		String fullUrl = getFullUrl(url);
		
		OpenData openData = new OpenData();
		
		if (!fullUrl.endsWith(".xml")) {
			String applicationInfoUrl;
			if (fullUrl.endsWith("/"))
				applicationInfoUrl = fullUrl.concat("applicationInfo.xml");
			else
				applicationInfoUrl = fullUrl.concat("/applicationInfo.xml");
			
			OpenData retryOpenData = checkAvaialable(applicationInfoUrl);
			if (retryOpenData.type.equals(OpenType.APPLICATION)){
				openData.url = retryOpenData.url;
				openData.type = retryOpenData.type;
			}
		}

		
		if (openData.type.equals(OpenType.ERROR) || openData.type.equals(OpenType.UNKNOWN)) {
			openData = checkAvaialable(fullUrl);
		}
		
		if (openData.type.equals(OpenType.ERROR)) {
			new AlertDialog.Builder(this).setTitle("Can't find resource").setMessage(openData.url).setPositiveButton("OK", null).show();
		} else if (openData.type.equals(OpenType.UNKNOWN)) {
			new AlertDialog.Builder(this).setTitle("Unknown resource type").setMessage(openData.url).setPositiveButton("OK", null).show();
		} else {
			addUrl(url);
			listView.invalidateViews();
			
			if (isUriSchemeOpen)
				finish();

			Intent intent = new Intent(MainActivity.this, MOMLAppActivity.class);
			intent.putExtra("url", openData.url);
			intent.putExtra("openType", openData.type);
			startActivity(intent);
		} 
		
	}
	
	class OpenType {
		public static final String UNKNOWN="unknown";
		public static final String ERROR="error";
		public static final String APPLICATION="application";
		public static final String MOMLUI="momlui";
		public static final String HTML="html";
	};

	String estimateOpenType(String text) {
		if (text != null) {
			String upperCaseText = text.toUpperCase(Locale.ENGLISH);
			int indexAppTag = upperCaseText.indexOf("<APPLICATIONINFO");
			int indexMomlUiTag = upperCaseText.indexOf("<UILAYOUT");
			int indexHTMLTag = upperCaseText.indexOf("<HTML");
			
			if (indexAppTag >= 0 && (indexMomlUiTag < 0 || indexAppTag < indexMomlUiTag) &&  (indexHTMLTag < 0 || indexAppTag < indexHTMLTag))
				return OpenType.APPLICATION;
			if (indexMomlUiTag >= 0 && (indexAppTag < 0 || indexMomlUiTag < indexAppTag) &&  (indexHTMLTag < 0 || indexMomlUiTag < indexHTMLTag))
				return OpenType.MOMLUI;
			if (indexHTMLTag >= 0 && (indexAppTag < 0 || indexHTMLTag < indexAppTag) &&  (indexMomlUiTag < 0 || indexHTMLTag < indexMomlUiTag))
				return OpenType.HTML;
		}

		return OpenType.UNKNOWN;
	}
	
	class OpenData {
		public String url = "";
		public String type = OpenType.UNKNOWN;
	}
	
	OpenData checkAvaialable(String fullUrl) {
		final OpenData openData = new OpenData();
		openData.url = fullUrl;
		openData.type = OpenType.UNKNOWN;
		
		if (fullUrl.startsWith("http")) {
			openData.type = OpenType.ERROR;
			final Object syncObject = new Object();
			final boolean errorConnect[] = new boolean[1]; 

			errorConnect[0] = true;
			synchronized (syncObject) {
				new Thread(new Runnable() {
					@Override
					public void run() {

						synchronized (syncObject) {
							try {
								URL url = new URL(openData.url);
								while(true) {
									HttpURLConnection connection = (HttpURLConnection) url.openConnection();
									connection.setConnectTimeout(5000);
									connection.connect();
									int responseCode = connection.getResponseCode();
									if (responseCode == HttpURLConnection.HTTP_OK) {
										openData.type = OpenType.UNKNOWN;
										InputStream is = new BufferedInputStream(connection.getInputStream());
										byte[] charByte = new byte[1024 * 32];
										int len = is.read(charByte);
										String text = new String(charByte, 0, len);
										openData.type = estimateOpenType(text);
										errorConnect[0] = false;
										connection.disconnect(); 
										break;
									} else if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
										openData.url = connection.getHeaderField("Location");
										url = new URL(openData.url);
										connection.disconnect(); 
									} else {
										connection.disconnect(); 
										break;
									}
								}
								
							} catch (Exception e) {
								e.printStackTrace();
							}
							syncObject.notify();
						}
					}
				}).start();
				try {
					syncObject.wait(5000);
				} catch (InterruptedException e) {
				}
			}
		} else if (fullUrl.startsWith("storage:")) { 
			openData.type = OpenType.ERROR;
			final String finalFullUrl = fullUrl.substring(8);
			final Object syncObject = new Object();
			final boolean errorConnect[] = new boolean[1]; 

			errorConnect[0] = true;
			synchronized (syncObject) {
				new Thread(new Runnable() {
					@Override
					public void run() {

						synchronized (syncObject) {
							try {
								openData.type = OpenType.UNKNOWN;
								InputStream is = new BufferedInputStream(new FileInputStream(finalFullUrl));
								byte[] charByte = new byte[1024 * 32];
								int len = is.read(charByte);
								String text = new String(charByte, 0, len);
								openData.type = estimateOpenType(text);
								errorConnect[0] = false;
								is.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
							syncObject.notify();
						}
					}
				}).start();
				try {
					syncObject.wait(5000);
				} catch (InterruptedException e) {
				}
			}
		} else {
			openData.type = OpenType.UNKNOWN;
			if (fullUrl.endsWith("applicationInfo.xml"))
				openData.type = OpenType.APPLICATION;
			if (fullUrl.endsWith(".xml"))
				openData.type = OpenType.MOMLUI;
			if (fullUrl.endsWith(".htm") || fullUrl.endsWith(".html"))
				openData.type = OpenType.HTML;
		}

		return openData;

	}

	void loadUrl() {
		urls.clear();
		int count = prefs.getInt("urlCount", 0);
		if (count == 0) {
			urls.add("mospi.org/momlApps/AgateApiDemo");
			urls.add("mospi.org/momlApps/AgateNews");
			urls.add("mospi.org/momlApps/CitrineApiDemo");
			urls.add("mospi.org/momlApps/ExTheme");
			urls.add("mospi.org/momlApps/MOMLAPI");
			urls.add("mospi.org/momlApps/ReadMe");
			return;
		}
		
		int i;
		for (i = 0; i < count; ++i) {
			String url = prefs.getString("url_" + i, "");
			if (url != null && url.length() > 0) {
				urls.add(url);
			}
		}
	}
	
	void saveUrl() {
		int count = urls.size();

		Editor edit = prefs.edit();
		
		edit.putInt("urlCount", count);
		int i;
		for (i = 0; i < count; ++i) {
			edit.putString("url_" + i, urls.get(i));
		}
		edit.commit();
	}
	
	boolean isExistUrl(String url) {
		return urls.contains(url);
	}
	
	void addUrl(String url) {
		if (urls.size() > 0 && url.equals(urls.get(0)))
			return;
		
		if (urls.contains(url)) {
			urls.remove(url);
		}
		urls.add(0, url);
		saveUrl();
	}
	
	String getFullUrl(String url) {
		if (!(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("embed:/") || url.startsWith("storage:/"))) {
			url = "http://" + url;
		}
		
		return url;
	}

//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.main, menu);
//		return true;
//	}

    @Override
    protected void onResume() {
    	super.onResume();
    	loadUrl();
		listView.invalidateViews();
    }
    
    
	private void createEditClearButton(EditText editText) {
		String value = "";// any text you are pre-filling in the EditText
		final String viewMode = "always";// never | editing | unlessEditing | always
		final String viewSide = "right"; // left | right
		final EditText et = editText;
		// et.setText(value);
		// your leftview, rightview or clearbuttonmode image. for clearbuttonmode this one from standard android images looks pretty good actually
		final Drawable x = getResources().getDrawable(android.R.drawable.ic_menu_close_clear_cancel);
		ColorMatrix matrix = new ColorMatrix();
	    matrix.setSaturation(0);
	    

	    ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);

	    x.setColorFilter(filter);
	    x.setAlpha(180);
	    
		x.setBounds(0, 0, x.getIntrinsicWidth(), x.getIntrinsicHeight());
		Drawable x2 = viewMode.equals("never") ? null : viewMode.equals("always") ? x : viewMode.equals("editing") ? (value.equals("") ? null : x) : viewMode.equals("unlessEditing") ? (value.equals("") ? x : null) : null;
		et.setCompoundDrawables(viewSide.equals("left") ? x2 : null, null, viewSide.equals("right") ? x2 : null, null);
		et.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (et.getCompoundDrawables()[viewSide.equals("left") ? 0 : 2] == null) {
					return false;
				}
				if (event.getAction() != MotionEvent.ACTION_UP) {
					return false;
				}
				// x pressed
				if ((viewSide.equals("left") && event.getX() < et.getPaddingLeft() + x.getIntrinsicWidth()) || (viewSide.equals("right") && event.getX() > et.getWidth() - et.getPaddingRight() - x.getIntrinsicWidth())) {
					Drawable x3 = viewMode.equals("never") ? null : viewMode.equals("always") ? x : viewMode.equals("editing") ? null : viewMode.equals("unlessEditing") ? x : null;
					et.setText("");
					et.setCompoundDrawables(viewSide.equals("left") ? x3 : null, null, viewSide.equals("right") ? x3 : null, null);
				}
				return false;
			}
		});
		et.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				Drawable x4 = viewMode.equals("never") ? null : viewMode.equals("always") ? x : viewMode.equals("editing") ? (et.getText().toString().equals("") ? null : x) : viewMode.equals("unlessEditing") ? (et.getText().toString().equals("") ? x : null) : null;
				et.setCompoundDrawables(viewSide.equals("left") ? x4 : null, null, viewSide.equals("right") ? x4 : null, null);
			}

			@Override
			public void afterTextChanged(Editable arg0) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
		});
    }
}
